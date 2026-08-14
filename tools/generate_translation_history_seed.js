#!/usr/bin/env node

const crypto = require("crypto");
const { execFile } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");
const zlib = require("zlib");

const REPO_ROOT = path.resolve(__dirname, "../../..");
const UPSTREAM_REPOSITORY = "https://github.com/zmh-program/gtb_platform";
const UPSTREAM_PATH = "lib/source/translations-data.json";
const API_BASE = "https://api.github.com/repos/zmh-program/gtb_platform";
const RAW_BASE = "https://raw.githubusercontent.com/zmh-program/gtb_platform";
const MANIFEST_FILE = "translation-history-seed.json";
const SNAPSHOT_BUNDLE_FILE = "translation-history-snapshots.bin";
const V2_BUNDLE_MAGIC = Buffer.from("GTBHIST2", "ascii");
const BUNDLE_MAGIC = Buffer.from("GTBHIST3", "ascii");
const V2_MANIFEST_SCHEMA = 2;
const MANIFEST_SCHEMA = 3;
const FETCH_CONCURRENCY = 4;
const MAX_V2_BUNDLE_ENTRIES = 1000;
const MAX_V2_COMPRESSED_SNAPSHOT_BYTES = 16 * 1024 * 1024;
const DELTA_BLOCK_BYTES = 32;
const DELTA_ANCHOR_STRIDE = 16;
const DELTA_ANCHORS_PER_HASH = 8;
const DELTA_HASH_BASE = 257;
const DELTA_COPY = 0;
const DELTA_ADD = 1;

const TARGETS = {
  legacy: "minecraft-mods/GtbStructures-main/versions/1.8.9/forge/src/main/resources",
  fabric: "minecraft-mods/GtbStructures-main/versions/1.21.10-1.21.11/fabric/src/main/resources",
  forge: "minecraft-mods/GtbStructures-main/versions/1.21.10-1.21.11/forge/src/main/resources",
};

function selectedTargets() {
  const targetArg = process.argv.find((arg) => arg.startsWith("--target="));
  if (!targetArg) return Object.entries(TARGETS);
  const name = targetArg.slice("--target=".length);
  if (!Object.prototype.hasOwnProperty.call(TARGETS, name)) {
    throw new Error(`Unknown target ${name}; expected ${Object.keys(TARGETS).join(", ")}`);
  }
  return [[name, TARGETS[name]]];
}

function sha256(bytes) {
  return crypto.createHash("sha256").update(bytes).digest("hex");
}

function request(url, accept) {
  return new Promise((resolve, reject) => {
    const command = process.platform === "win32" ? "curl.exe" : "curl";
    const temp = path.join(os.tmpdir(),
      `gtbs-translation-${process.pid}-${crypto.randomBytes(8).toString("hex")}.tmp`);
    const cleanup = () => {
      if (fs.existsSync(temp)) fs.unlinkSync(temp);
    };
    const args = [
      "--fail", "--location", "--silent", "--show-error",
      "--retry", "5", "--retry-all-errors", "--retry-delay", "1",
      "--remove-on-error", "--output", temp,
      "--connect-timeout", "20", "--max-time", "120",
      "--header", `Accept: ${accept}`,
      "--header", "User-Agent: GTBS-translation-history-generator",
      url,
    ];
    execFile(command, args, { encoding: null, maxBuffer: 1024 * 1024 },
      (error, stdout, stderr) => {
        if (error) {
          cleanup();
          reject(new Error(`${command} failed for ${url}: ${stderr.toString("utf8").trim()}`));
          return;
        }
        try {
          const bytes = fs.readFileSync(temp);
          cleanup();
          resolve(bytes);
        } catch (readError) {
          cleanup();
          reject(readError);
        }
      });
  });
}

async function fetchCommitHistory() {
  const commits = [];
  for (let page = 1; ; page += 1) {
    const url = `${API_BASE}/commits?path=${encodeURIComponent(UPSTREAM_PATH)}`
      + `&per_page=100&page=${page}`;
    const body = await request(url, "application/vnd.github+json");
    const batch = JSON.parse(body.toString("utf8"));
    if (!Array.isArray(batch)) throw new Error("GitHub commit response was not an array");
    commits.push(...batch);
    if (batch.length < 100) break;
  }
  if (commits.length === 0) throw new Error("GitHub returned no translation history");
  return commits.reverse();
}

async function mapLimit(values, limit, mapper) {
  const results = new Array(values.length);
  let cursor = 0;
  async function worker() {
    while (cursor < values.length) {
      const index = cursor;
      cursor += 1;
      results[index] = await mapper(values[index], index);
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, values.length) }, worker));
  return results;
}

function validateTranslationJson(bytes, commit) {
  let parsed;
  try {
    parsed = JSON.parse(bytes.toString("utf8"));
  } catch (error) {
    throw new Error(`Invalid translation JSON at ${commit}: ${error.message}`);
  }
  if (!Array.isArray(parsed) || parsed.length < 100) {
    throw new Error(`Invalid translation snapshot at ${commit}`);
  }
}

async function fetchSnapshots(commits) {
  return mapLimit(commits, FETCH_CONCURRENCY, async (commit) => {
    const sourceTimestamp = Date.parse(commit.commit.author.date);
    if (!Number.isFinite(sourceTimestamp) || sourceTimestamp <= 0) {
      throw new Error(`Invalid author timestamp for ${commit.sha}`);
    }
    const rawUrl = `${RAW_BASE}/${commit.sha}/${UPSTREAM_PATH}`;
    const bytes = await request(rawUrl, "application/json");
    validateTranslationJson(bytes, commit.sha);
    process.stderr.write(`Fetched ${commit.sha.slice(0, 8)} (${bytes.length} bytes).\n`);
    return {
      commit: commit.sha,
      sourceTimestamp,
      hash: sha256(bytes),
      rawBytes: bytes,
    };
  });
}

function buildHistory(records) {
  const versions = [];
  const snapshots = new Map();
  let previousTimestamp = 0;
  for (const record of records) {
    if (record.sourceTimestamp <= previousTimestamp) {
      throw new Error(`Non-increasing upstream author timestamp at ${record.commit}`);
    }
    previousTimestamp = record.sourceTimestamp;
    snapshots.set(record.hash, record);
    const previous = versions[versions.length - 1];
    if (previous && previous.hash === record.hash) continue;
    versions.push({
      hash: record.hash,
      validFrom: versions.length === 0 ? 0 : record.sourceTimestamp,
      validUntil: 0,
      sourceTimestamp: record.sourceTimestamp,
      sourceCommit: record.commit,
      bundledSnapshot: true,
    });
  }
  for (let index = 0; index + 1 < versions.length; index += 1) {
    versions[index].validUntil = versions[index + 1].validFrom;
  }
  return { versions, snapshots };
}

function writeUint32(value, label) {
  if (!Number.isInteger(value) || value < 0 || value > 0xFFFFFFFF) {
    throw new Error(`${label} is outside the unsigned 32-bit range: ${value}`);
  }
  const bytes = Buffer.alloc(4);
  bytes.writeUInt32BE(value, 0);
  return bytes;
}

function rollingHash(bytes, offset) {
  let hash = 0;
  for (let index = 0; index < DELTA_BLOCK_BYTES; index += 1) {
    hash = (Math.imul(hash, DELTA_HASH_BASE) + bytes[offset + index]) >>> 0;
  }
  return hash;
}

function rollingHashNext(hash, removed, added, highestPower) {
  const withoutFirst = (hash - Math.imul(removed, highestPower)) >>> 0;
  return (Math.imul(withoutFirst, DELTA_HASH_BASE) + added) >>> 0;
}

function anchorIndex(base) {
  const index = new Map();
  if (base.length < DELTA_BLOCK_BYTES) return index;

  let highestPower = 1;
  for (let position = 1; position < DELTA_BLOCK_BYTES; position += 1) {
    highestPower = Math.imul(highestPower, DELTA_HASH_BASE) >>> 0;
  }
  let hash = rollingHash(base, 0);
  for (let position = 0; position + DELTA_BLOCK_BYTES <= base.length; position += 1) {
    if (position % DELTA_ANCHOR_STRIDE === 0) {
      const candidates = index.get(hash);
      if (candidates === undefined) {
        index.set(hash, [position]);
      } else if (candidates.length < DELTA_ANCHORS_PER_HASH) {
        candidates.push(position);
      }
    }
    if (position + DELTA_BLOCK_BYTES < base.length) {
      hash = rollingHashNext(
        hash, base[position], base[position + DELTA_BLOCK_BYTES], highestPower);
    }
  }
  return index;
}

function blockEquals(left, leftOffset, right, rightOffset) {
  for (let index = 0; index < DELTA_BLOCK_BYTES; index += 1) {
    if (left[leftOffset + index] !== right[rightOffset + index]) return false;
  }
  return true;
}

function matchLength(base, baseOffset, target, targetOffset) {
  let length = DELTA_BLOCK_BYTES;
  while (baseOffset + length < base.length
      && targetOffset + length < target.length
      && base[baseOffset + length] === target[targetOffset + length]) {
    length += 1;
  }
  return length;
}

function copyOperation(baseOffset, length) {
  const operation = Buffer.alloc(9);
  operation[0] = DELTA_COPY;
  operation.writeUInt32BE(baseOffset, 1);
  operation.writeUInt32BE(length, 5);
  return operation;
}

function addOperation(bytes) {
  const header = Buffer.alloc(5);
  header[0] = DELTA_ADD;
  header.writeUInt32BE(bytes.length, 1);
  return Buffer.concat([header, bytes]);
}

function buildPatch(base, target) {
  const anchors = anchorIndex(base);
  const operations = [];
  let highestPower = 1;
  for (let position = 1; position < DELTA_BLOCK_BYTES; position += 1) {
    highestPower = Math.imul(highestPower, DELTA_HASH_BASE) >>> 0;
  }

  let targetOffset = 0;
  let literalStart = 0;
  let targetHash = target.length >= DELTA_BLOCK_BYTES ? rollingHash(target, 0) : null;
  while (targetOffset < target.length) {
    let bestOffset = 0;
    let bestLength = 0;
    if (targetHash !== null) {
      const candidates = anchors.get(targetHash);
      if (candidates !== undefined) {
        for (const baseOffset of candidates) {
          if (!blockEquals(base, baseOffset, target, targetOffset)) continue;
          const length = matchLength(base, baseOffset, target, targetOffset);
          if (length > bestLength || (length === bestLength && baseOffset < bestOffset)) {
            bestOffset = baseOffset;
            bestLength = length;
          }
        }
      }
    }

    if (bestLength >= DELTA_BLOCK_BYTES) {
      if (literalStart < targetOffset) {
        operations.push(addOperation(target.subarray(literalStart, targetOffset)));
      }
      operations.push(copyOperation(bestOffset, bestLength));
      targetOffset += bestLength;
      literalStart = targetOffset;
      targetHash = targetOffset + DELTA_BLOCK_BYTES <= target.length
        ? rollingHash(target, targetOffset)
        : null;
      continue;
    }

    const previousOffset = targetOffset;
    targetOffset += 1;
    targetHash = targetOffset + DELTA_BLOCK_BYTES <= target.length
      ? rollingHashNext(
        targetHash,
        target[previousOffset],
        target[previousOffset + DELTA_BLOCK_BYTES],
        highestPower)
      : null;
  }
  if (literalStart < target.length) operations.push(addOperation(target.subarray(literalStart)));
  return Buffer.concat([writeUint32(operations.length, "patch operation count")].concat(operations));
}

function patchUint32(patch, offset, label) {
  if (offset + 4 > patch.length) throw new Error(`Truncated patch ${label}`);
  return patch.readUInt32BE(offset);
}

function applyPatch(base, patch, expectedLength) {
  const operationCount = patchUint32(patch, 0, "operation count");
  const output = Buffer.alloc(expectedLength);
  let patchOffset = 4;
  let outputOffset = 0;
  for (let operationIndex = 0; operationIndex < operationCount; operationIndex += 1) {
    if (patchOffset >= patch.length) throw new Error("Truncated patch operation");
    const kind = patch[patchOffset];
    patchOffset += 1;
    if (kind === DELTA_COPY) {
      const baseOffset = patchUint32(patch, patchOffset, "COPY base offset");
      const length = patchUint32(patch, patchOffset + 4, "COPY length");
      patchOffset += 8;
      if (baseOffset + length > base.length || outputOffset + length > output.length) {
        throw new Error("COPY operation exceeds its input or output bounds");
      }
      base.copy(output, outputOffset, baseOffset, baseOffset + length);
      outputOffset += length;
    } else if (kind === DELTA_ADD) {
      const length = patchUint32(patch, patchOffset, "ADD length");
      patchOffset += 4;
      if (patchOffset + length > patch.length || outputOffset + length > output.length) {
        throw new Error("ADD operation exceeds its input or output bounds");
      }
      patch.copy(output, outputOffset, patchOffset, patchOffset + length);
      patchOffset += length;
      outputOffset += length;
    } else {
      throw new Error(`Unknown patch operation ${kind}`);
    }
  }
  if (patchOffset !== patch.length || outputOffset !== output.length) {
    throw new Error("Patch does not exactly reconstruct the expected output length");
  }
  return output;
}

function gzip(bytes) {
  return zlib.gzipSync(bytes, { level: 9, mtime: 0 });
}

function isSha256(value) {
  return typeof value === "string" && /^[0-9a-f]{64}$/.test(value);
}

function readV2Bundle(bytes, label) {
  if (bytes.length < V2_BUNDLE_MAGIC.length + 4
      || !bytes.subarray(0, V2_BUNDLE_MAGIC.length).equals(V2_BUNDLE_MAGIC)) {
    throw new Error(`${label} does not have a GTBHIST2 header`);
  }
  const count = bytes.readUInt32BE(V2_BUNDLE_MAGIC.length);
  if (count === 0 || count > MAX_V2_BUNDLE_ENTRIES) {
    throw new Error(`${label} has invalid entry count ${count}`);
  }

  const snapshots = new Map();
  let offset = V2_BUNDLE_MAGIC.length + 4;
  for (let entry = 0; entry < count; entry += 1) {
    if (offset + 36 > bytes.length) throw new Error(`${label} is truncated at entry ${entry}`);
    const hash = bytes.subarray(offset, offset + 32).toString("hex");
    offset += 32;
    const payloadLength = bytes.readUInt32BE(offset);
    offset += 4;
    if (payloadLength === 0 || payloadLength > MAX_V2_COMPRESSED_SNAPSHOT_BYTES
        || offset + payloadLength > bytes.length) {
      throw new Error(`${label} has invalid compressed length at entry ${entry}`);
    }
    const payload = bytes.subarray(offset, offset + payloadLength);
    offset += payloadLength;
    let raw;
    try {
      raw = zlib.gunzipSync(payload);
    } catch (error) {
      throw new Error(`${label} cannot decompress entry ${entry}: ${error.message}`);
    }
    if (sha256(raw) !== hash) {
      throw new Error(`${label} SHA-256 mismatch at entry ${entry}`);
    }
    validateTranslationJson(raw, `${label} entry ${entry}`);
    if (snapshots.has(hash)) throw new Error(`${label} repeats snapshot ${hash}`);
    snapshots.set(hash, { rawBytes: raw });
  }
  if (offset !== bytes.length) throw new Error(`${label} has trailing bytes`);
  return snapshots;
}

function validateV2Manifest(manifest, label) {
  if (manifest === null || typeof manifest !== "object"
      || manifest.schemaVersion !== V2_MANIFEST_SCHEMA
      || manifest.upstreamRepository !== UPSTREAM_REPOSITORY
      || manifest.upstreamPath !== UPSTREAM_PATH
      || !Array.isArray(manifest.versions)
      || manifest.versions.length === 0) {
    throw new Error(`${label} is not a supported schema-2 translation history manifest`);
  }
  const versions = manifest.versions;
  const hashes = new Set();
  for (let index = 0; index < versions.length; index += 1) {
    const version = versions[index];
    if (version === null || typeof version !== "object"
        || !isSha256(version.hash)
        || !Number.isSafeInteger(version.validFrom)
        || !Number.isSafeInteger(version.validUntil)
        || !Number.isSafeInteger(version.sourceTimestamp)
        || version.validFrom < 0
        || version.validUntil < 0
        || version.sourceTimestamp <= 0
        || !/^[0-9a-f]{40}$/.test(version.sourceCommit || "")
        || version.bundledSnapshot !== true
        || hashes.has(version.hash)) {
      throw new Error(`${label} has invalid version ${index}`);
    }
    if ((index === 0 && version.validFrom !== 0)
        || (index > 0 && version.validFrom <= versions[index - 1].validFrom)) {
      throw new Error(`${label} has non-chronological version ${index}`);
    }
    hashes.add(version.hash);
  }
  for (let index = 0; index + 1 < versions.length; index += 1) {
    if (versions[index].validUntil !== versions[index + 1].validFrom) {
      throw new Error(`${label} has a gap after version ${index}`);
    }
  }
  const latest = versions[versions.length - 1];
  if (latest.validUntil !== 0
      || manifest.currentHash !== latest.hash
      || manifest.currentValidFrom !== latest.validFrom) {
    throw new Error(`${label} has an invalid current version`);
  }
  return versions;
}

function readV2Seed(resourceDir, targetName) {
  const manifestPath = path.join(resourceDir, MANIFEST_FILE);
  const bundlePath = path.join(resourceDir, SNAPSHOT_BUNDLE_FILE);
  let manifest;
  try {
    manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
  } catch (error) {
    throw new Error(`Could not read ${targetName} schema-2 manifest: ${error.message}`);
  }
  const versions = validateV2Manifest(manifest, `${targetName} manifest`);
  let bundleBytes;
  try {
    bundleBytes = fs.readFileSync(bundlePath);
  } catch (error) {
    throw new Error(`Could not read ${targetName} GTBHIST2 bundle: ${error.message}`);
  }
  const snapshots = readV2Bundle(bundleBytes, `${targetName} GTBHIST2 bundle`);
  if (snapshots.size !== versions.length) {
    throw new Error(`${targetName} manifest and bundle have different version counts`);
  }
  for (const version of versions) {
    if (!snapshots.has(version.hash)) {
      throw new Error(`${targetName} bundle is missing manifest snapshot ${version.hash}`);
    }
  }
  return { versions, snapshots };
}

function buildManifest(versions) {
  const latest = versions[versions.length - 1];
  return {
    schemaVersion: MANIFEST_SCHEMA,
    currentHash: latest.hash,
    currentValidFrom: latest.validFrom,
    upstreamRepository: UPSTREAM_REPOSITORY,
    upstreamPath: UPSTREAM_PATH,
    bundleKind: "GTBHIST3-sequential-delta",
    versions,
  };
}

function validateCurrentTranslations(targets, expectedHash) {
  for (const [name, resourceDir] of targets) {
    const absoluteDir = path.join(REPO_ROOT, resourceDir);
    const currentHash = sha256(fs.readFileSync(path.join(absoluteDir, "translations-data.json")));
    if (currentHash !== expectedHash) {
      throw new Error(`${name} translations-data.json is ${currentHash}, expected ${expectedHash}`);
    }
  }
}

function migrateV2(targets) {
  let result = null;
  for (const [name, resourceDir] of targets) {
    const seed = readV2Seed(path.join(REPO_ROOT, resourceDir), name);
    const manifestBytes = Buffer.from(`${JSON.stringify(buildManifest(seed.versions), null, 2)}\n`, "utf8");
    const bundleBytes = buildBundle(seed.versions, seed.snapshots);
    if (result === null) {
      result = { manifestBytes, bundleBytes, versions: seed.versions };
    } else if (!result.manifestBytes.equals(manifestBytes) || !result.bundleBytes.equals(bundleBytes)) {
      throw new Error("Selected schema-2 resources are not identical across loader targets");
    }
  }
  return result;
}

function buildBundle(versions, snapshots) {
  const count = Buffer.alloc(4);
  count.writeUInt32BE(versions.length, 0);
  const chunks = [BUNDLE_MAGIC, count];
  let previousRaw = null;
  for (let index = 0; index < versions.length; index += 1) {
    const version = versions[index];
    const snapshot = snapshots.get(version.hash);
    if (snapshot === undefined) throw new Error(`Missing raw snapshot ${version.hash}`);
    const raw = snapshot.rawBytes;
    if (sha256(raw) !== version.hash) {
      throw new Error(`Raw snapshot hash mismatch for ${version.hash}`);
    }
    const kind = index === 0 ? 0 : 1;
    const patch = kind === 0 ? raw : buildPatch(previousRaw, raw);
    const payload = gzip(patch);
    const reconstructed = kind === 0
      ? zlib.gunzipSync(payload)
      : applyPatch(previousRaw, zlib.gunzipSync(payload), raw.length);
    if (!reconstructed.equals(raw) || sha256(reconstructed) !== version.hash) {
      throw new Error(`Could not self-verify bundled snapshot ${version.hash}`);
    }
    chunks.push(
      Buffer.from([kind]),
      Buffer.from(version.hash, "hex"),
      writeUint32(raw.length, "raw snapshot length"),
      writeUint32(payload.length, "gzip payload length"),
      payload,
    );
    previousRaw = raw;
  }
  return Buffer.concat(chunks);
}

function assertSelfTest(condition, message) {
  if (!condition) throw new Error(`Self-test failed: ${message}`);
}

function assertSelfTestThrows(action, message) {
  try {
    action();
  } catch (error) {
    return;
  }
  throw new Error(`Self-test failed: ${message}`);
}

function runSelfTest() {
  const repeated = Buffer.from("repeatable-block-".repeat(96), "utf8");
  const snapshots = [
    Buffer.concat([Buffer.from("prefix|", "utf8"), repeated, Buffer.from("|suffix", "utf8")]),
    Buffer.concat([
      Buffer.from("prefix|inserted|", "utf8"),
      repeated.subarray(0, 800),
      Buffer.from("|middle|", "utf8"),
      repeated.subarray(800),
      Buffer.from("|suffix", "utf8"),
    ]),
    Buffer.concat([
      Buffer.from("prefix|", "utf8"),
      repeated.subarray(800),
      Buffer.from("|reordered|", "utf8"),
      repeated.subarray(0, 800),
      Buffer.from("|suffix", "utf8"),
    ]),
    Buffer.concat([Buffer.from("prefix|", "utf8"), repeated.subarray(0, 480), Buffer.from("|suffix", "utf8")]),
    Buffer.from("all-add target with no shared 32-byte block", "utf8"),
  ];
  const versions = snapshots.map((raw, index) => ({ hash: sha256(raw), index }));
  const snapshotMap = new Map(versions.map((version, index) => [version.hash, {
    rawBytes: snapshots[index],
  }]));

  for (let index = 1; index < snapshots.length; index += 1) {
    const patch = buildPatch(snapshots[index - 1], snapshots[index]);
    const restored = applyPatch(snapshots[index - 1], patch, snapshots[index].length);
    assertSelfTest(restored.equals(snapshots[index]), `round trip ${index}`);
    assertSelfTest(sha256(restored) === versions[index].hash, `hash ${index}`);
  }
  const allAddPatch = buildPatch(snapshots[snapshots.length - 2], snapshots[snapshots.length - 1]);
  assertSelfTest(allAddPatch.readUInt32BE(0) === 1 && allAddPatch[4] === DELTA_ADD,
    "later snapshots use an ADD-only delta when no COPY exists");

  const first = buildBundle(versions, snapshotMap);
  const second = buildBundle(versions, snapshotMap);
  assertSelfTest(first.equals(second), "bundle bytes are deterministic");
  assertSelfTest(first.subarray(0, BUNDLE_MAGIC.length).equals(BUNDLE_MAGIC), "bundle magic");
  assertSelfTest(first.readUInt32BE(BUNDLE_MAGIC.length) === snapshots.length, "bundle count");
  let offset = BUNDLE_MAGIC.length + 4;
  for (let index = 0; index < snapshots.length; index += 1) {
    assertSelfTest(first[offset] === (index === 0 ? 0 : 1), `entry kind ${index}`);
    const rawLength = first.readUInt32BE(offset + 33);
    const payloadLength = first.readUInt32BE(offset + 37);
    assertSelfTest(rawLength === snapshots[index].length, `entry raw length ${index}`);
    offset += 41 + payloadLength;
  }
  assertSelfTest(offset === first.length, "bundle layout");

  const v2Raw = [
    Buffer.from(JSON.stringify(Array.from({ length: 100 }, (_, index) => ({
      theme: `V2 Theme ${index}`,
    }))), "utf8"),
    Buffer.from(JSON.stringify(Array.from({ length: 101 }, (_, index) => ({
      theme: `V2 Updated Theme ${index}`,
    }))), "utf8"),
  ];
  const v2Versions = v2Raw.map((raw, index) => ({
    hash: sha256(raw),
    validFrom: index === 0 ? 0 : 2000,
    validUntil: index === 0 ? 2000 : 0,
    sourceTimestamp: 1000 + (index * 1000),
    sourceCommit: `${index + 1}`.repeat(40),
    bundledSnapshot: true,
  }));
  const v2Chunks = [V2_BUNDLE_MAGIC, writeUint32(v2Raw.length, "v2 self-test count")];
  for (let index = 0; index < v2Raw.length; index += 1) {
    const payload = gzip(v2Raw[index]);
    v2Chunks.push(Buffer.from(v2Versions[index].hash, "hex"),
      writeUint32(payload.length, "v2 self-test payload length"), payload);
  }
  const v2Bundle = Buffer.concat(v2Chunks);
  const decodedV2 = readV2Bundle(v2Bundle, "v2 self-test bundle");
  assertSelfTest(decodedV2.size === v2Versions.length, "v2 bundle count");
  for (let index = 0; index < v2Raw.length; index += 1) {
    assertSelfTest(decodedV2.get(v2Versions[index].hash).rawBytes.equals(v2Raw[index]),
      `v2 bundle entry ${index}`);
  }
  validateV2Manifest({
    schemaVersion: V2_MANIFEST_SCHEMA,
    currentHash: v2Versions[1].hash,
    currentValidFrom: v2Versions[1].validFrom,
    upstreamRepository: UPSTREAM_REPOSITORY,
    upstreamPath: UPSTREAM_PATH,
    versions: v2Versions,
  }, "v2 self-test manifest");
  const corruptedV2 = Buffer.from(v2Bundle);
  corruptedV2[V2_BUNDLE_MAGIC.length + 4 + 32 + 4] ^= 0xFF;
  assertSelfTestThrows(() => readV2Bundle(corruptedV2, "corrupt v2 self-test bundle"),
    "v2 decoder rejects corrupted gzip data");
}

function writeAtomic(file, bytes) {
  const temp = `${file}.tmp-${process.pid}`;
  fs.writeFileSync(temp, bytes);
  fs.renameSync(temp, file);
}

async function main() {
  if (process.argv.includes("--self-test")) {
    runSelfTest();
    process.stdout.write("Translation history generator self-test PASS.\n");
    return;
  }
  const targets = selectedTargets();
  if (process.argv.includes("--migrate-v2")) {
    const migration = migrateV2(targets);
    const latest = migration.versions[migration.versions.length - 1];
    validateCurrentTranslations(targets, latest.hash);
    if (!process.argv.includes("--dry-run")) {
      for (const [, resourceDir] of targets) {
        const absoluteDir = path.join(REPO_ROOT, resourceDir);
        writeAtomic(path.join(absoluteDir, MANIFEST_FILE), migration.manifestBytes);
        writeAtomic(path.join(absoluteDir, SNAPSHOT_BUNDLE_FILE), migration.bundleBytes);
      }
    }
    process.stdout.write(
      `${process.argv.includes("--dry-run") ? "Validated" : "Migrated"} `
        + `${migration.versions.length} schema-2 translation versions into GTBHIST3 `
        + `(${migration.bundleBytes.length} bundled bytes) for `
        + `${targets.map(([name]) => name).join(", ")}.\n`
        + `Earliest ${migration.versions[0].sourceCommit} is valid from 0; current `
        + `${latest.sourceCommit} is valid from ${latest.validFrom}.\n`,
    );
    return;
  }
  const commits = await fetchCommitHistory();
  const records = await fetchSnapshots(commits);
  const { versions, snapshots } = buildHistory(records);
  const latest = versions[versions.length - 1];
  const manifest = buildManifest(versions);
  const manifestBytes = Buffer.from(`${JSON.stringify(manifest, null, 2)}\n`, "utf8");
  const bundleBytes = buildBundle(versions, snapshots);

  for (const [name, resourceDir] of targets) {
    const absoluteDir = path.join(REPO_ROOT, resourceDir);
    const currentFile = path.join(absoluteDir, "translations-data.json");
    const currentHash = sha256(fs.readFileSync(currentFile));
    if (currentHash !== latest.hash) {
      throw new Error(`${name} translations-data.json is ${currentHash}, expected ${latest.hash}`);
    }
    if (!process.argv.includes("--dry-run")) {
      writeAtomic(path.join(absoluteDir, MANIFEST_FILE), manifestBytes);
      writeAtomic(path.join(absoluteDir, SNAPSHOT_BUNDLE_FILE), bundleBytes);
    }
  }

  const rawBytes = Array.from(snapshots.values())
    .reduce((total, snapshot) => total + snapshot.rawBytes.length, 0);
  process.stdout.write(
    `${process.argv.includes("--dry-run") ? "Validated" : "Wrote"} `
      + `${versions.length} GitHub translation versions (${snapshots.size} unique snapshots, `
      + `${rawBytes} raw bytes, ${bundleBytes.length} bundled bytes) for `
      + `${targets.map(([name]) => name).join(", ")}.\n`
      + `Earliest ${versions[0].sourceCommit} is valid from 0; current `
      + `${latest.sourceCommit} is valid from ${latest.validFrom}.\n`,
  );
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message}\n`);
  process.exitCode = 1;
});
