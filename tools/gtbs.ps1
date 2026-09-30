[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('fast', 'parity', 'release')]
    [string]$Mode,

    [string]$Target,
    [string]$Area,
    [string[]]$Task = @(),
    [switch]$PlanOnly,
    [switch]$Json,
    [switch]$NoDaemon,
    [string]$DeployTo
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$versionsRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'versions'))
$manifestPath = Join-Path $PSScriptRoot 'verification-scopes.json'

function Get-NamedProperty {
    param(
        [Parameter(Mandatory = $true)]$Object,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $property = $Object.PSObject.Properties |
        Where-Object { $_.Name -ieq $Name } |
        Select-Object -First 1
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Resolve-TargetDefinition {
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$Name
    )

    foreach ($candidate in @($Manifest.targets)) {
        if ($candidate.id -ieq $Name -or @($candidate.aliases) -icontains $Name) {
            return $candidate
        }
    }
    $known = @($Manifest.targets | ForEach-Object { $_.id }) -join ', '
    throw "Unknown target '$Name'. Expected one of: $known"
}

function Get-JavaMajorFromHome {
    param([Parameter(Mandatory = $true)][string]$JavaHome)

    $releaseFile = Join-Path $JavaHome 'release'
    if (Test-Path -LiteralPath $releaseFile) {
        $versionLine = Get-Content -LiteralPath $releaseFile |
            Where-Object { $_ -match '^JAVA_VERSION=' } |
            Select-Object -First 1
        if ($versionLine -match 'JAVA_VERSION="(?<version>[^"]+)"') {
            $version = $Matches.version
            if ($version -match '^1\.(?<major>[0-9]+)') {
                return [int]$Matches.major
            }
            if ($version -match '^(?<major>[0-9]+)') {
                return [int]$Matches.major
            }
        }
    }
    return $null
}

function Resolve-JavaHome {
    param(
        [Parameter(Mandatory = $true)][int]$Major,
        [switch]$AllowMissing
    )

    $overrideName = "GTBS_JAVA${Major}_HOME"
    $override = [Environment]::GetEnvironmentVariable($overrideName)
    $candidates = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($override)) {
        $candidates.Add($override)
    }

    $searchRoots = @(
        (Join-Path $env:ProgramFiles 'Java'),
        (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
        (Join-Path $env:USERPROFILE '.jdks'),
        'D:\GTBS-26x-toolchains'
    ) | Select-Object -Unique

    foreach ($searchRoot in $searchRoots) {
        if (-not (Test-Path -LiteralPath $searchRoot -PathType Container)) { continue }
        Get-ChildItem -LiteralPath $searchRoot -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { $candidates.Add($_.FullName) }
    }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        $java = Join-Path $candidate 'bin\java.exe'
        $javac = Join-Path $candidate 'bin\javac.exe'
        if (-not (Test-Path -LiteralPath $java -PathType Leaf) -or
                -not (Test-Path -LiteralPath $javac -PathType Leaf)) { continue }
        if ((Get-JavaMajorFromHome -JavaHome $candidate) -eq $Major) {
            return [IO.Path]::GetFullPath($candidate)
        }
    }

    if ($AllowMissing) { return $null }
    throw "Java $Major was not found. Set $overrideName to its JDK directory."
}

function Add-UniqueTask {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [System.Collections.Generic.List[string]]$Tasks,
        [Parameter(Mandatory = $true)][string]$Name
    )

    if ($Name -notmatch '^[A-Za-z][A-Za-z0-9:_-]*$') {
        throw "Invalid Gradle task name '$Name'."
    }
    if (-not $Tasks.Contains($Name)) { $Tasks.Add($Name) }
}

function Get-AreaDefinition {
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $definition = Get-NamedProperty -Object $Manifest.areas -Name $Name
    if ($null -eq $definition) {
        $known = @($Manifest.areas.PSObject.Properties.Name) -join ', '
        throw "Unknown area '$Name'. Expected one of: $known"
    }
    return $definition
}

function New-TargetPlan {
    param(
        [Parameter(Mandatory = $true)]$Definition,
        $AreaDefinition,
        [Parameter(Mandatory = $true)][string]$PlanMode,
        [string[]]$ExtraTasks,
        [switch]$AllowMissingJava,
        [string]$DeploymentTarget
    )

    $root = [IO.Path]::GetFullPath((Join-Path $projectRoot $Definition.root))
    if (-not $root.StartsWith($versionsRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Target '$($Definition.id)' escapes the versions directory."
    }
    if (-not (Test-Path -LiteralPath (Join-Path $root 'gradlew.bat') -PathType Leaf)) {
        throw "Target '$($Definition.id)' has no Gradle wrapper at $root"
    }

    $tasks = New-Object System.Collections.Generic.List[string]
    if ($PlanMode -eq 'release') {
        foreach ($name in @($Definition.releaseTasks)) {
            Add-UniqueTask -Tasks $tasks -Name $name
        }
    } else {
        Add-UniqueTask -Tasks $tasks -Name 'build'
        if ($null -ne $AreaDefinition) {
            $scopedTasks = Get-NamedProperty -Object $AreaDefinition.targets -Name $Definition.id
            foreach ($name in @($scopedTasks)) {
                Add-UniqueTask -Tasks $tasks -Name $name
            }
        }
        foreach ($name in @($ExtraTasks)) {
            Add-UniqueTask -Tasks $tasks -Name $name
        }
    }

    $options = New-Object System.Collections.Generic.List[string]
    $options.Add('--offline')
    $options.Add('--console=plain')
    if ($PlanMode -eq 'release' -or $NoDaemon) {
        $options.Add('--no-daemon')
    } else {
        $options.Add('--daemon')
    }

    $javaArguments = @{
        Major = [int]$Definition.java
        AllowMissing = [bool]$AllowMissingJava
    }
    $javaHome = Resolve-JavaHome @javaArguments
    return [PSCustomObject]@{
        mode = $PlanMode
        target = $Definition.id
        root = $root
        java = [int]$Definition.java
        javaHome = $javaHome
        tasks = @($tasks)
        options = @($options)
        artifact = [IO.Path]::GetFullPath((Join-Path $root $Definition.artifact))
        deployTo = $DeploymentTarget
    }
}

function Write-HumanPlan {
    param([Parameter(Mandatory = $true)][object[]]$Plans)

    foreach ($plan in $Plans) {
        Write-Output "[$($plan.mode)] $($plan.target)"
        Write-Output "  Root: $($plan.root)"
        $javaDisplay = if ($null -eq $plan.javaHome) {
            "Java $($plan.java) unresolved"
        } else {
            $plan.javaHome
        }
        Write-Output "  Java: $javaDisplay"
        Write-Output "  Tasks: $($plan.tasks -join ' ')"
        Write-Output "  Options: $($plan.options -join ' ')"
        if (-not [string]::IsNullOrWhiteSpace($plan.deployTo)) {
            Write-Output "  Deploy: $($plan.artifact) -> $($plan.deployTo)"
        }
    }
}

function Assert-NoJavaProcess {
    $javaProcesses = @(Get-Process -Name java, javaw -ErrorAction SilentlyContinue)
    if ($javaProcesses.Count -ne 0) {
        $ids = @($javaProcesses | ForEach-Object { $_.Id }) -join ', '
        throw "Java is running in process(es) $ids; deployment aborted."
    }
}

function Stop-GtbsGradleDaemons {
    $stoppedFamilies = New-Object 'System.Collections.Generic.HashSet[string]'
    $previousJavaHome = $env:JAVA_HOME
    $previousPath = $env:Path
    try {
        foreach ($definition in @($manifest.targets)) {
            $root = [IO.Path]::GetFullPath((Join-Path $projectRoot $definition.root))
            $wrapper = Join-Path $root 'gradlew.bat'
            $wrapperProperties = Join-Path $root 'gradle\wrapper\gradle-wrapper.properties'
            $distribution = Get-Content -LiteralPath $wrapperProperties |
                Where-Object { $_ -match '^distributionUrl=' } |
                Select-Object -First 1
            $javaHome = Resolve-JavaHome -Major ([int]$definition.java)
            $family = "$javaHome|$distribution"
            if (-not $stoppedFamilies.Add($family)) { continue }

            $env:JAVA_HOME = $javaHome
            $env:Path = (Join-Path $javaHome 'bin') + ';' + $previousPath
            Push-Location $root
            try {
                & $wrapper --stop
                if ($LASTEXITCODE -ne 0) {
                    throw "Unable to stop the $($definition.id) Gradle daemon family."
                }
            } finally {
                Pop-Location
            }
        }
    } finally {
        $env:JAVA_HOME = $previousJavaHome
        $env:Path = $previousPath
    }
}

function Deploy-Artifact {
    param([Parameter(Mandatory = $true)]$Plan)

    if (-not (Test-Path -LiteralPath $Plan.artifact -PathType Leaf)) {
        throw "Expected artifact was not produced: $($Plan.artifact)"
    }
    if (-not (Test-Path -LiteralPath $Plan.deployTo -PathType Leaf)) {
        throw "Deployment target must already exist: $($Plan.deployTo)"
    }

    $target = [IO.Path]::GetFullPath($Plan.deployTo)
    $targetDirectory = Split-Path -Parent $target
    $active = @(Get-ChildItem -LiteralPath $targetDirectory -File -Filter '*.jar' |
        Where-Object { $_.Name -match '(?i)(gtbstructures|gtbs)' })
    if ($active.Count -ne 1 -or
            -not [StringComparer]::OrdinalIgnoreCase.Equals($active[0].FullName, $target)) {
        throw "Expected exactly one active GTBS JAR matching the deployment target in $targetDirectory"
    }

    Assert-NoJavaProcess
    Copy-Item -LiteralPath $Plan.artifact -Destination $target -Force
    $sourceHash = (Get-FileHash -LiteralPath $Plan.artifact -Algorithm SHA256).Hash
    $targetHash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
    if ($sourceHash -ne $targetHash) {
        throw "Deployment hash mismatch for $target"
    }
    Write-Output "Deployed $($Plan.target): $target [$targetHash]"
}

if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw "Verification manifest is missing: $manifestPath"
}
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ([int]$manifest.schemaVersion -ne 1) {
    throw "Unsupported verification manifest schema: $($manifest.schemaVersion)"
}

if ($Mode -eq 'fast' -and [string]::IsNullOrWhiteSpace($Target)) {
    throw 'fast mode requires -Target.'
}
if ($Mode -ne 'release' -and [string]::IsNullOrWhiteSpace($Area) -and $Task.Count -eq 0) {
    throw "$Mode mode requires -Area or at least one -Task."
}
if ($Mode -eq 'parity' -and -not [string]::IsNullOrWhiteSpace($Target)) {
    throw 'parity mode selects all targets for the area; do not pass -Target.'
}
if ($Mode -eq 'release' -and
        (-not [string]::IsNullOrWhiteSpace($Target) -or
         -not [string]::IsNullOrWhiteSpace($Area) -or
         $Task.Count -ne 0)) {
    throw 'release mode always uses the complete manifest; omit -Target, -Area, and -Task.'
}
if (-not [string]::IsNullOrWhiteSpace($DeployTo) -and $Mode -ne 'fast') {
    throw '-DeployTo is supported only in fast mode with one explicit target.'
}

$areaDefinition = if ([string]::IsNullOrWhiteSpace($Area)) {
    $null
} else {
    Get-AreaDefinition -Manifest $manifest -Name $Area
}

$definitions = @()
if ($Mode -eq 'fast') {
    $definitions = @(Resolve-TargetDefinition -Manifest $manifest -Name $Target)
} elseif ($Mode -eq 'parity') {
    $areaTargetNames = @($areaDefinition.targets.PSObject.Properties.Name)
    foreach ($definition in @($manifest.targets)) {
        if ($areaTargetNames -icontains $definition.id) {
            $definitions += $definition
        }
    }
} else {
    $definitions = @($manifest.targets)
}

$plans = @()
foreach ($definition in $definitions) {
    $deploymentTarget = if ($Mode -eq 'fast') { $DeployTo } else { $null }
    $planArguments = @{
        Definition = $definition
        AreaDefinition = $areaDefinition
        PlanMode = $Mode
        ExtraTasks = $Task
        AllowMissingJava = [bool]$PlanOnly
        DeploymentTarget = $deploymentTarget
    }
    $plans += New-TargetPlan @planArguments
}

if ($PlanOnly) {
    if ($Json) {
        ConvertTo-Json -InputObject @($plans) -Depth 6
    } else {
        Write-HumanPlan -Plans $plans
    }
    return
}
if ($Json) {
    throw '-Json is supported only with -PlanOnly.'
}

foreach ($plan in $plans) {
    Write-Output "Running $($plan.mode) verification for $($plan.target)..."
    $wrapper = Join-Path $plan.root 'gradlew.bat'
    $arguments = @($plan.tasks) + @($plan.options)
    $stopwatch = [Diagnostics.Stopwatch]::StartNew()
    $previousJavaHome = $env:JAVA_HOME
    $previousPath = $env:Path
    $env:JAVA_HOME = $plan.javaHome
    $env:Path = (Join-Path $plan.javaHome 'bin') + ';' + $previousPath
    Push-Location $plan.root
    try {
        & $wrapper @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle failed for $($plan.target) with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
        $env:JAVA_HOME = $previousJavaHome
        $env:Path = $previousPath
        $stopwatch.Stop()
    }
    Write-Output ("Verified {0} in {1:n1}s." -f $plan.target, $stopwatch.Elapsed.TotalSeconds)

    if (-not [string]::IsNullOrWhiteSpace($plan.deployTo)) {
        Stop-GtbsGradleDaemons
        Deploy-Artifact -Plan $plan
    }
}
