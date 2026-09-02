# Generates Zmittag.xcodeproj/project.pbxproj from the file tree.
# Usage: pwsh / powershell -File Scripts\generate-project.ps1
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

function Get-Id([string]$key) {
    $md5 = [System.Security.Cryptography.MD5]::Create()
    $hash = $md5.ComputeHash([System.Text.Encoding]::UTF8.GetBytes("ZmittagProject::" + $key))
    -join ($hash[0..11] | ForEach-Object { $_.ToString("x2") }).ToUpper()
}

# ---------- collect files ----------
$sharedFiles = @(
    "App/Core/Restaurant.swift",
    "App/Core/Models.swift",
    "App/Core/DayResolver.swift",
    "App/Core/MenuTypeNormalizer.swift",
    "App/Core/Theme.swift",
    "App/Core/ShareText.swift",
    "App/Data/MenuAPI.swift",
    "App/Data/JSONCache.swift",
    "App/Data/SettingsStore.swift"
)

function KindOf([string]$name) {
    if ($name -like "*.swift") { return "swift" }
    if ($name -like "*.strings") { return "strings" }
    if ($name -like "*.xcassets") { return "assets" }
    if ($name -like "*.entitlements") { return "meta" }
    if ($name -like "*.plist") { return "meta" }
    return "meta"
}

$allFiles = @{}   # relPath (with slashes) -> kind
# Asset catalogs are directories on disk, add them explicitly.
$allFiles["App/Assets.xcassets"] = "assets"
# App files (contents of asset catalogs are not listed individually)
Get-ChildItem -LiteralPath (Join-Path $root "App") -Recurse -File | Where-Object { $_.FullName -notlike "*.xcassets*" } | ForEach-Object {
    $rel = "App/" + ($_.FullName.Substring((Join-Path $root "App").Length + 1) -replace "\\", "/")
    $allFiles[$rel] = KindOf $_.Name
}
# Widget files
Get-ChildItem -LiteralPath (Join-Path $root "Widget") -Recurse -File | ForEach-Object {
    $rel = "Widget/" + ($_.FullName.Substring((Join-Path $root "Widget").Length + 1) -replace "\\", "/")
    $allFiles[$rel] = KindOf $_.Name
}

$sharedSet = @{}
foreach ($f in $sharedFiles) { $sharedSet[$f] = $true }
if ($sharedSet.Count -ne $sharedFiles.Count) { throw "duplicate shared entries" }
foreach ($f in $sharedFiles) { if (-not $allFiles.ContainsKey($f)) { throw "shared file missing: $f" } }

$appFiles = @($allFiles.Keys | Where-Object { $_ -like "App/*" } | Sort-Object)
$widgetFiles = @(($allFiles.Keys | Where-Object { $_ -like "Widget/*" } | Sort-Object) + ($sharedFiles | Sort-Object))
$widgetSet = @{}; foreach ($f in $widgetFiles) { $widgetSet[$f] = $true }

# sanity: no duplicate widget entries
if ($widgetFiles.Count -ne $widgetSet.Count) { throw "duplicate widget file entries" }

$swiftApp = @($appFiles | Where-Object { $allFiles[$_] -eq "swift" })
$swiftWidget = @($widgetFiles | Where-Object { $allFiles[$_] -eq "swift" })
$resApp = @($appFiles | Where-Object { $allFiles[$_] -eq "strings" -or $allFiles[$_] -eq "assets" })
$resWidget = @($widgetFiles | Where-Object { $allFiles[$_] -eq "strings" })

# ---------- IDs ----------
$projectId    = Get-Id "project"
$mainGroup    = Get-Id "grp:"
$productsGrp  = Get-Id "grp:Products"
$appTarget    = Get-Id "target:Zmittag"
$widgetTarget = Get-Id "target:ZmittagWidget"
$appProduct   = Get-Id "product:Zmittag.app"
$widgetProduct= Get-Id "product:ZmittagWidget.appex"
$proxy        = Get-Id "proxy"
$targetDep    = Get-Id "target-dep"
$copyFile     = Get-Id "bf:copy:widget-product"

function FrId([string]$rel) { Get-Id ("fr:" + $rel) }
function BfId([string]$target, [string]$rel) { Get-Id ("bf:" + $target + ":" + $rel) }
function GrpId([string]$path) { Get-Id ("grp:" + $path) }

function LastComponent([string]$rel) { ($rel -split "/")[-1] }

function FileTypeFor([string]$rel) {
    switch -Regex ($rel) {
        '\.swift$' { "sourcecode.swift" }
        '\.strings$' { "text.plist.strings" }
        '\.xcassets$' { "folder.assetcatalog" }
        '\.entitlements$' { "text.plist.entitlements" }
        '\.plist$' { "text.plist.xml" }
        default { "text" }
    }
}

# ---------- group tree ----------
function New-Node([string]$name, [string]$path) {
    [pscustomobject]@{ Name = $name; Path = $path; Groups = [System.Collections.Specialized.OrderedDictionary]::new(); Files = @() }
}

$rootNode = New-Node "" ""

function Add-File([string]$rel) {
    $parts = $rel -split "/"
    $fileName = $parts[-1]
    $dirParts = $parts[0..($parts.Count - 2)]
    $cursor = $rootNode
    foreach ($part in $dirParts) {
        if (-not $cursor.Groups.Contains($part)) {
            $cursor.Groups[$part] = New-Node $part $part
        }
        $cursor = $cursor.Groups[$part]
    }
    if ($cursor.Files -notcontains $rel) { $cursor.Files += $rel }
}

foreach ($rel in ($allFiles.Keys | Sort-Object)) { Add-File $rel }

$buildFileLines = [System.Collections.Generic.List[string]]::new()
$fileRefLines = [System.Collections.Generic.List[string]]::new()
$groupLines = [System.Collections.Generic.List[string]]::new()

function Emit-Node($node, $lines) {
    $id = GrpId ($node.Path)
    $children = [System.Collections.Generic.List[string]]::new()
    foreach ($rel in $node.Files) {
        $children.Add(("{0} /* {1} */" -f (FrId $rel), (LastComponent $rel)))
    }
    foreach ($g in $node.Groups.Values) {
        $children.Add(("{0} /* {1} */" -f (GrpId $g.Path), $g.Name))
    }
    $childrenText = ($children | ForEach-Object { "`t`t`t`t" + $_ + "," }) -join "`n"
    $pathLine = ""
    if ($node.Path) { $pathLine = "`n`t`t`t`tPath = " + $node.Path + ";" }
    $lines.Add(("{0} /* {1} */ = {{`n`t`t`t`tisa = PBXGroup;`n`t`t`t`tchildren = (`n{2}`n`t`t`t`t);{3}`n`t`t`t`tsourceTree = `"<group>`";`n`t`t}};" -f $id, $node.Name, $childrenText, $pathLine))
    foreach ($g in $node.Groups.Values) { Emit-Node $g $lines }
}

# The root/main group is assembled by hand below (it also holds Products); emit only its children.
foreach ($g in $rootNode.Groups.Values) { Emit-Node $g $groupLines }

foreach ($rel in ($allFiles.Keys | Sort-Object)) {
    $name = LastComponent $rel
    $fileRefLines.Add(("{0} /* {1} */ = {{`n`t`t`t`tisa = PBXFileReference;`n`t`t`t`tlastKnownFileType = {2};`n`t`t`t`tname = {3};`n`t`t`t`tpath = {3};`n`t`t`t`tsourceTree = `"<group>`";`n`t`t}};" -f (FrId $rel), $name, (FileTypeFor $rel), $name))
}

# build files
foreach ($rel in $swiftApp) {
    $buildFileLines.Add(("{0} /* {1} in Sources */ = {{isa = PBXBuildFile; fileRef = {2} /* {3} */; }};" -f (BfId "app" $rel), (LastComponent $rel), (FrId $rel), (LastComponent $rel)))
}
foreach ($rel in $resApp) {
    $buildFileLines.Add(("{0} /* {1} in Resources */ = {{isa = PBXBuildFile; fileRef = {2} /* {3} */; }};" -f (BfId "app" $rel), (LastComponent $rel), (FrId $rel), (LastComponent $rel)))
}
foreach ($rel in $swiftWidget) {
    $buildFileLines.Add(("{0} /* {1} in Sources */ = {{isa = PBXBuildFile; fileRef = {2} /* {3} */; }};" -f (BfId "widget" $rel), (LastComponent $rel), (FrId $rel), (LastComponent $rel)))
}
foreach ($rel in $resWidget) {
    $buildFileLines.Add(("{0} /* {1} in Resources */ = {{isa = PBXBuildFile; fileRef = {2} /* {3} */; }};" -f (BfId "widget" $rel), (LastComponent $rel), (FrId $rel), (LastComponent $rel)))
}
$buildFileLines.Add(("{0} /* ZmittagWidget.appex in Embed App Extensions */ = {{isa = PBXBuildFile; fileRef = {1} /* ZmittagWidget.appex */; settings = {{ATTRIBUTES = (RemoveHeadersOnCopy,);}};}};" -f $copyFile, $widgetProduct))

$srcAppRefs = @($swiftApp | ForEach-Object { "{0} /* {1} in Sources */" -f (BfId "app" $_), (LastComponent $_) })
$resAppRefs = @($resApp | ForEach-Object { "{0} /* {1} in Resources */" -f (BfId "app" $_), (LastComponent $_) })
$srcWidgetRefs = @($swiftWidget | ForEach-Object { "{0} /* {1} in Sources */" -f (BfId "widget" $_), (LastComponent $_) })
$resWidgetRefs = @($resWidget | ForEach-Object { "{0} /* {1} in Resources */" -f (BfId "widget" $_), (LastComponent $_) })

# ---------- build configurations ----------
$projConfList = Get-Id "proj-conf-list"
$projDebug    = Get-Id "proj-conf:Debug"
$projRelease  = Get-Id "proj-conf:Release"
$appConfList  = Get-Id "app-conf-list"
$appDebug     = Get-Id "app-conf:Debug"
$appRelease   = Get-Id "app-conf:Release"
$widgetConfList = Get-Id "widget-conf-list"
$widgetDebug  = Get-Id "widget-conf:Debug"
$widgetRelease= Get-Id "widget-conf:Release"

$commonProject = @'
		ARCHS = "$(ARCHS_STANDARD)";
		CLANG_ANALYZER_NONNULL = YES;
		CLANG_ANALYZER_NUMBER_OBJECT_CONVERSION = YES_AGGRESSIVE;
		CLANG_CXX_LANGUAGE_STANDARD = "gnu++20";
		CLANG_ENABLE_MODULES = YES;
		CLANG_ENABLE_OBJC_ARC = YES;
		CLANG_ENABLE_OBJC_WEAK = YES;
		CLANG_WARN_BLOCK_CAPTURE_AUTORELEASING = YES;
		CLANG_WARN_BOOL_CONVERSION = YES;
		CLANG_WARN_COMMA = YES;
		CLANG_WARN_CONSTANT_CONVERSION = YES;
		CLANG_WARN_DEPRECATED_OBJC_IMPLEMENTATIONS = YES;
		CLANG_WARN_DIRECT_OBJC_ISA_USAGE = YES_ERROR;
		CLANG_WARN_DOCUMENTATION_COMMENTS = YES;
		CLANG_WARN_EMPTY_BODY = YES;
		CLANG_WARN_ENUM_CONVERSION = YES;
		CLANG_WARN_INFINITE_RECURSION = YES;
		CLANG_WARN_INT_CONVERSION = YES;
		CLANG_WARN_NON_LITERAL_NULL_CONVERSION = YES;
		CLANG_WARN_OBJC_IMPLICIT_RETAIN_SELF = YES;
		CLANG_WARN_OBJC_LITERAL_CONVERSION = YES;
		CLANG_WARN_OBJC_ROOT_CLASS = YES_ERROR;
		CLANG_WARN_QUOTED_INCLUDE_IN_FRAMEWORK_HEADER = YES;
		CLANG_WARN_RANGE_LOOP_ANALYSIS = YES;
		CLANG_WARN_STRICT_PROTOTYPES = YES;
		CLANG_WARN_SUSPICIOUS_MOVE = YES;
		CLANG_WARN_UNGUARDED_AVAILABILITY = YES_AGGRESSIVE;
		CLANG_WARN_UNREACHABLE_CODE = YES;
		CLANG_WARN__DUPLICATE_METHOD_MATCH = YES;
		COPY_PHASE_STRIP = NO;
		ENABLE_USER_SCRIPT_SANDBOXING = YES;
		GCC_C_LANGUAGE_STANDARD = gnu17;
		GCC_NO_COMMON_BLOCKS = YES;
		GCC_WARN_64_TO_32_BIT_CONVERSION = YES;
		GCC_WARN_ABOUT_RETURN_TYPE = YES_ERROR;
		GCC_WARN_UNDECLARED_SELECTOR = YES;
		GCC_WARN_UNINITIALIZED_AUTOS = YES_AGGRESSIVE;
		GCC_WARN_UNUSED_FUNCTION = YES;
		GCC_WARN_UNUSED_VARIABLE = YES;
		IPHONEOS_DEPLOYMENT_TARGET = 17.0;
		SDKROOT = iphoneos;
		SWIFT_VERSION = 5.0;
'@

function ProjConf([string]$id, [string]$name, [string[]]$extra) {
    $out = [System.Collections.Generic.List[string]]::new()
    $out.Add("`t`t$id /* $name */ = {")
    $out.Add("`t`t`t`tisa = XCBuildConfiguration;")
    $out.Add("`t`t`t`tbuildSettings = {")
    foreach ($s in $commonProject) { $out.Add("`t`t`t`t`t" + $s.TrimStart("`t")) }
    foreach ($s in $extra) { $out.Add("`t`t`t`t`t" + $s) }
    $out.Add("`t`t`t`};")
    $out.Add("`t`t`t`tname = $name;")
    $out.Add("`t`t};")
    return ($out -join "`n")
}

$projDebugBlock = ProjConf $projDebug "Debug" @(
	"alwaysSearchUserPath = NO;",
	"debugInfoFormat = dwarf;",
	"enableTestability = YES;",
	"gccDynamicNoPic = NO;",
	"gccOptimizationLevel = 0;",
	'"gccPreprocessorDefinitions" = ("DEBUG=1", "$(inherited)");',
	"mtlEnableDebugInfo = INCLUDE_SOURCE;",
	"onlyActiveArch = YES;",
	'"swiftActiveCompilationConditions" = "DEBUG $(inherited)";',
	"swiftOptimizationLevel = -Onone;"
)
$projReleaseBlock = ProjConf $projRelease "Release" @(
	"alwaysSearchUserPath = NO;",
	'"debugInfoFormat" = "dwarf-with-dsym";',
	"mtlEnableDebugInfo = NO;",
	"swiftOptimizationLevel = -O;",
	"validateProduct = YES;"
)

$appSettings = @'
		ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
		ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor;
		CODE_SIGN_ENTITLEMENTS = App/Zmittag.entitlements;
		CODE_SIGN_STYLE = Automatic;
		CURRENT_PROJECT_VERSION = 1;
		ENABLE_PREVIEWS = YES;
		GENERATE_INFOPLIST_FILE = YES;
		INFOPLIST_KEY_CFBundleDisplayName = Zmittag;
		INFOPLIST_KEY_UIApplicationSceneManifest_Generation = YES;
		INFOPLIST_KEY_UIApplicationSupportsIndirectInputEvents = YES;
		INFOPLIST_KEY_UILaunchScreen_Generation = YES;
		INFOPLIST_KEY_UISupportedInterfaceOrientations = UIInterfaceOrientationPortrait;
		LD_RUNPATH_SEARCH_PATHS = ("$(inherited)", "@executable_path/Frameworks");
		MARKETING_VERSION = 1.0.0;
		PRODUCT_BUNDLE_IDENTIFIER = dev.davidemarcoli.zmittag;
		PRODUCT_NAME = "$(TARGET_NAME)";
		SWIFT_EMIT_LOC_STRINGS = YES;
		SWIFT_VERSION = 5.0;
		TARGETED_DEVICE_FAMILY = 1;
'@

$widgetSettings = @'
		CODE_SIGN_ENTITLEMENTS = Widget/ZmittagWidget.entitlements;
		CODE_SIGN_STYLE = Automatic;
		CURRENT_PROJECT_VERSION = 1;
		ENABLE_PREVIEWS = YES;
		INFOPLIST_FILE = Widget/Info.plist;
		LD_RUNPATH_SEARCH_PATHS = ("$(inherited)", "@executable_path/Frameworks", "@loader_path/../Frameworks");
		MARKETING_VERSION = 1.0.0;
		PRODUCT_BUNDLE_IDENTIFIER = dev.davidemarcoli.zmittag.widget;
		PRODUCT_NAME = "$(TARGET_NAME)";
		SKIP_INSTALL = YES;
		SWIFT_EMIT_LOC_STRINGS = YES;
		SWIFT_VERSION = 5.0;
		TARGETED_DEVICE_FAMILY = 1;
'@

function ConfBlock([string]$id, [string]$name, [string]$settings) {
    $out = [System.Collections.Generic.List[string]]::new()
    $out.Add("`t`t$id /* $name */ = {")
    $out.Add("`t`t`t`tisa = XCBuildConfiguration;")
    $out.Add("`t`t`t`tbuildSettings = {")
    foreach ($s in ($settings -split "`n")) { if ($s.Trim()) { $out.Add("`t`t`t`t`t" + $s.TrimStart("`t")) } }
    $out.Add("`t`t`t`};")
    $out.Add("`t`t`t`tname = $name;")
    $out.Add("`t`t};")
    return ($out -join "`n")
}

$appDebugBlock = ConfBlock $appDebug "Debug" $appSettings
$appReleaseBlock = ConfBlock $appRelease "Release" $appSettings
$widgetDebugBlock = ConfBlock $widgetDebug "Debug" $widgetSettings
$widgetReleaseBlock = ConfBlock $widgetRelease "Release" $widgetSettings

function ConfList([string]$id, [string]$debug, [string]$release) {
	"`t`t{0} = {{`n`t`t`t`tisa = XCConfigurationList;`n`t`t`t`tbuildConfigurations = (`n`t`t`t`t`t{1} /* Debug */,`n`t`t`t`t`t{2} /* Release */,`n`t`t`t`t);`n`t`t`t`tdefaultConfigurationIsVisible = 0;`n`t`t`t`tdefaultConfigurationName = Release;`n`t`t}};" -f $id, $debug, $release
}

# ---------- assemble ----------
$L = [System.Collections.Generic.List[string]]::new()
$L.Add("// !`$*UTF8*`$!")
$L.Add("{")
$L.Add("`tarchiveVersion = 1;")
$L.Add("`tclasses = {")
$L.Add("`t};")
$L.Add("`tobjectVersion = 56;")
$L.Add("`tobjects = {")
$L.Add("")
$L.Add("/* Begin PBXBuildFile section */")
foreach ($line in ($buildFileLines | Sort-Object)) { $L.Add("`t`t" + $line) }
$L.Add("/* End PBXBuildFile section */")
$L.Add("")
$L.Add("/* Begin PBXContainerItemProxy section */")
$L.Add("`t`t$proxy = {")
$L.Add("`t`t`t`tisa = PBXContainerItemProxy;")
$L.Add("`t`t`t`tcontainerPortal = $projectId /* Project object */;")
$L.Add("`t`t`t`tproxyType = 1;")
$L.Add("`t`t`t`tremoteGlobalIDString = $widgetTarget;")
$L.Add("`t`t`t`tremoteInfo = ZmittagWidget;")
$L.Add("`t`t};")
$L.Add("/* End PBXContainerItemProxy section */")
$L.Add("")
$L.Add("/* Begin PBXCopyFilesBuildPhase section */")
$L.Add("`t`t$(Get-Id 'app-copyfiles') /* Embed App Extensions */ = {")
$L.Add("`t`t`t`tisa = PBXCopyFilesBuildPhase;")
$L.Add("`t`t`t`tbuildActionMask = 2147483647;")
$L.Add("`t`t`t`tdstPath = `"`";")
$L.Add("`t`t`t`tdstSubfolderSpec = 10;")
$L.Add("`t`t`t`tfiles = (")
$L.Add("`t`t`t`t`t$copyFile /* ZmittagWidget.appex in Embed App Extensions */,")
$L.Add("`t`t`t`t);")
$L.Add("`t`t`t`tname = `"Embed App Extensions`";")
$L.Add("`t`t`t`trunOnlyForDeploymentPostprocessing = 0;")
$L.Add("`t`t};")
$L.Add("/* End PBXCopyFilesBuildPhase section */")
$L.Add("")
$L.Add("/* Begin PBXFileReference section */")
foreach ($line in ($fileRefLines | Sort-Object)) { $L.Add("`t`t" + $line) }
$L.Add("`t`t$appProduct /* Zmittag.app */ = {")
$L.Add("`t`t`t`tisa = PBXFileReference;")
$L.Add("`t`t`t`texplicitFileType = wrapper.application;")
$L.Add("`t`t`t`tincludeInIndex = 0;")
$L.Add("`t`t`t`tproductName = Zmittag.app;")
$L.Add("`t`t`t`tsourceTree = BUILT_PRODUCTS_DIR;")
$L.Add("`t`t};")
$L.Add("`t`t$widgetProduct /* ZmittagWidget.appex */ = {")
$L.Add("`t`t`t`tisa = PBXFileReference;")
$L.Add("`t`t`t`texplicitFileType = `"wrapper.app-extension`";")
$L.Add("`t`t`t`tincludeInIndex = 0;")
$L.Add("`t`t`t`tproductName = ZmittagWidget.appex;")
$L.Add("`t`t`t`tsourceTree = BUILT_PRODUCTS_DIR;")
$L.Add("`t`t};")
$L.Add("/* End PBXFileReference section */")
$L.Add("")
$L.Add("/* Begin PBXFrameworksBuildPhase section */")
foreach ($t in @(@{ id = (Get-Id "app-frameworks"); name = "" }, @{ id = (Get-Id "widget-frameworks"); name = "" })) {
    $L.Add("`t`t$($t.id) = {")
    $L.Add("`t`t`t`tisa = PBXFrameworksBuildPhase;")
    $L.Add("`t`t`t`tbuildActionMask = 2147483647;")
    $L.Add("`t`t`t`tfiles = (")
    $L.Add("`t`t`t`t);")
    $L.Add("`t`t`t`trunOnlyForDeploymentPostprocessing = 0;")
    $L.Add("`t`t};")
}
$L.Add("/* End PBXFrameworksBuildPhase section */")
$L.Add("")
$L.Add("/* Begin PBXGroup section */")
foreach ($line in $groupLines) { $L.Add("`t`t" + $line) }
$L.Add("`t`t$mainGroup = {")
$L.Add("`t`t`t`tisa = PBXGroup;")
$L.Add("`t`t`t`tchildren = (")
$L.Add("`t`t`t`t`t$(Get-Id 'grp:App') /* App */,")
$L.Add("`t`t`t`t`t$(Get-Id 'grp:Widget') /* Widget */,")
$L.Add("`t`t`t`t`t$productsGrp /* Products */,")
$L.Add("`t`t`t`t);")
$L.Add("`t`t`t`tsourceTree = `"<>`";")
$L.Add("`t`t};")
$L.Add("`t`t$productsGrp /* Products */ = {")
$L.Add("`t`t`t`tisa = PBXGroup;")
$L.Add("`t`t`t`tchildren = (")
$L.Add("`t`t`t`t`t$appProduct /* Zmittag.app */,")
$L.Add("`t`t`t`t`t$widgetProduct /* ZmittagWidget.appex */,")
$L.Add("`t`t`t`t);")
$L.Add("`t`t`t`tname = Products;")
$L.Add("`t`t`t`tsourceTree = `"<>`";")
$L.Add("`t`t};")
$L.Add("/* End PBXGroup section */")
$L.Add("")
$L.Add("/* Begin PBXNativeTarget section */")
$L.Add("`t`t$appTarget /* Zmittag */ = {")
$L.Add("`t`t`t`tisa = PBXNativeTarget;")
$L.Add("`t`t`t`tbuildConfigurationList = $appConfList;")
$L.Add("`t`t`t`tbuildPhases = (")
$L.Add("`t`t`t`t`t$(Get-Id 'app-sources') /* Sources */,")
$L.Add("`t`t`t`t`t$(Get-Id 'app-frameworks') /* Frameworks */,")
$L.Add("`t`t`t`t`t$(Get-Id 'app-resources') /* Resources */,")
$L.Add("`t`t`t`t`t$(Get-Id 'app-copyfiles') /* Embed App Extensions */,")
$L.Add("`t`t`t`t);")
$L.Add("`t`t`t`tbuildRules = (")
$L.Add("`t`t`t`t);")
$L.Add("`t`t`t`tdependencies = (")
$L.Add("`t`t`t`t`t$targetDep /* PBXTargetDependency */,")
$L.Add("`t`t`t`t);")
$L.Add("`t`t`t`tname = Zmittag;")
$L.Add("`t`t`t`tproductName = Zmittag;")
$L.Add("`t`t`t`tproductReference = $appProduct;")
$L.Add("`t`t`t`tproductType = `"com.apple.product-type.application`";")
$L.Add("`t`t};")
$L.Add("`t`t$widgetTarget /* ZmittagWidget */ = {")
$L.Add("`t`t`t`tisa = PBXNativeTarget;")
$L.Add("`t`t`t`tbuildConfigurationList = $widgetConfList;")
$L.Add("`t`t`t`tbuildPhases = (")
$L.Add("`t`t`t`t`t$(Get-Id 'widget-sources') /* Sources */,")
$L.Add("`t`t`t`t`t$(Get-Id 'widget-frameworks') /* Frameworks */,")
$L.Add("`t`t`t`t`t$(Get-Id 'widget-resources') /* Resources */,")
$L.Add("`t`t`t`t);")
$L.Add("`t`t`t`tbuildRules = (")
$L.Add("`t`t`t`t);")
$L.Add("`t`t`t`tdependencies = (")
$L.Add("`t`t`t`t);")
$L.Add("`t`t`t`tname = ZmittagWidget;")
$L.Add("`t`t`t`tproductName = ZmittagWidget;")
$L.Add("`t`t`t`tproductReference = $widgetProduct;")
$L.Add("`t`t`t`tproductType = `"com.apple.product-type.app-extension`";")
$L.Add("`t`t};")
$L.Add("/* End PBXNativeTarget section */")
$L.Add("")
$L.Add("/* Begin PBXProject section */")
$L.Add("`t`t$projectId /* Project object */ = {")
$L.Add("`t`t`t`tisa = PBXProject;")
$L.Add("`t`t`t`tattributes = {")
$L.Add("`t`t`t`t`tBuildIndependentTargetsInParallel = 1;")
$L.Add("`t`t`t`t`tLastSwiftUpdateCheck = 1600;")
$L.Add("`t`t`t`t`tLastUpgradeCheck = 1600;")
$L.Add("`t`t`t`t`tTargetAttributes = {")
$L.Add("`t`t`t`t`t`t$appTarget = {")
$L.Add("`t`t`t`t`t`t`tCreatedOnToolsVersion = 16.0;")
$L.Add("`t`t`t`t`t`t};")
$L.Add("`t`t`t`t`t`t$widgetTarget = {")
$L.Add("`t`t`t`t`t`t`tCreatedOnToolsVersion = 16.0;")
$L.Add("`t`t`t`t`t`t};")
$L.Add("`t`t`t`t`t};")
$L.Add("`t`t`t`t};")
$L.Add("`t`t`t`tbuildConfigurationList = $projConfList;")
$L.Add("`t`t`t`tcompatibilityVersion = `"Xcode 14.0`";")
$L.Add("`t`t`t`tdevelopmentRegion = en;")
$L.Add("`t`t`t`thasScannedForEncodings = 0;")
$L.Add("`t`t`t`tknownRegions = (")
$L.Add("`t`t`t`t`ten,")
$L.Add("`t`t`t`t`tde,")
$L.Add("`t`t`t`t`tBase,")
$L.Add("`t`t`t`t);")
$L.Add("`t`t`t`tmainGroup = $mainGroup;")
$L.Add("`t`t`t`tproductRefGroup = $productsGrp /* Products */;")
$L.Add("`t`t`t`tprojectDirPath = `"`";")
$L.Add("`t`t`t`tprojectRoot = `"`";")
$L.Add("`t`t`t`ttargets = (")
$L.Add("`t`t`t`t`t$appTarget /* Zmittag */,")
$L.Add("`t`t`t`t`t$widgetTarget /* ZmittagWidget */,")
$L.Add("`t`t`t`t);")
$L.Add("`t`t};")
$L.Add("/* End PBXProject section */")
$L.Add("")
$L.Add("/* Begin PBXResourcesBuildPhase section */")
$L.Add("`t`t$(Get-Id 'app-resources') /* Resources */ = {")
$L.Add("`t`t`t`tisa = PBXResourcesBuildPhase;")
$L.Add("`t`t`t`tbuildActionMask = 2147483647;")
$L.Add("`t`t`t`tfiles = (")
foreach ($r in $resAppRefs) { $L.Add("`t`t`t`t`t$r,") }
$L.Add("`t`t`t`t);")
$L.Add("`t`t`t`trunOnlyForDeploymentPostprocessing = 0;")
$L.Add("`t`t};")
$L.Add("`t`t$(Get-Id 'widget-resources') /* Resources */ = {")
$L.Add("`t`t`t`tisa = PBXResourcesBuildPhase;")
$L.Add("`t`t`t`tbuildActionMask = 2147483647;")
$L.Add("`t`t`t`tfiles = (")
foreach ($r in $resWidgetRefs) { $L.Add("`t`t`t`t`t$r,") }
$L.Add("`t`t`t`t);")
$L.Add("`t`t`t`trunOnlyForDeploymentPostprocessing = 0;")
$L.Add("`t`t};")
$L.Add("/* End PBXResourcesBuildPhase section */")
$L.Add("")
$L.Add("/* Begin PBXSourcesBuildPhase section */")
$L.Add("`t`t$(Get-Id 'app-sources') /* Sources */ = {")
$L.Add("`t`t`t`tisa = PBXSourcesBuildPhase;")
$L.Add("`t`t`t`tbuildActionMask = 2147483647;")
$L.Add("`t`t`t`tfiles = (")
foreach ($r in $srcAppRefs) { $L.Add("`t`t`t`t`t$r,") }
$L.Add("`t`t`t`t);")
$L.Add("`t`t`t`trunOnlyForDeploymentPostprocessing = 0;")
$L.Add("`t`t};")
$L.Add("`t`t$(Get-Id 'widget-sources') /* Sources */ = {")
$L.Add("`t`t`t`tisa = PBXSourcesBuildPhase;")
$L.Add("`t`t`t`tbuildActionMask = 2147483647;")
$L.Add("`t`t`t`tfiles = (")
foreach ($r in $srcWidgetRefs) { $L.Add("`t`t`t`t`t$r,") }
$L.Add("`t`t`t`t);")
$L.Add("`t`t`t`trunOnlyForDeploymentPostprocessing = 0;")
$L.Add("`t`t};")
$L.Add("/* End PBXSourcesBuildPhase section */")
$L.Add("")
$L.Add("/* Begin PBXTargetDependency section */")
$L.Add("`t`t$targetDep /* PBXTargetDependency */ = {")
$L.Add("`t`t`t`tisa = PBXTargetDependency;")
$L.Add("`t`t`t`ttarget = $widgetTarget /* ZmittagWidget */;")
$L.Add("`t`t`t`ttargetProxy = $proxy;")
$L.Add("`t`t};")
$L.Add("/* End PBXTargetDependency section */")
$L.Add("")
$L.Add("/* Begin XCBuildConfiguration section */")
$L.Add($projDebugBlock)
$L.Add($projReleaseBlock)
$L.Add($appDebugBlock)
$L.Add($appReleaseBlock)
$L.Add($widgetDebugBlock)
$L.Add($widgetReleaseBlock)
$L.Add("/* End XCBuildConfiguration section */")
$L.Add("")
$L.Add("/* Begin XCConfigurationList section */")
$L.Add((ConfList $projConfList $projDebug $projRelease))
$L.Add((ConfList $appConfList $appDebug $appRelease))
$L.Add((ConfList $widgetConfList $widgetDebug $widgetRelease))
$L.Add("/* End XCConfigurationList section */")
$L.Add("")
$L.Add("`t};")
$L.Add("`trootObject = $projectId /* Project object */;")
$L.Add("}")

$out = (Join-Path $root "Zmittag.xcodeproj/project.pbxproj")
[System.IO.File]::WriteAllText($out, ($L -join "`n") + "`n", (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Wrote $out"
Write-Host "App sources: $($swiftApp.Count), App resources: $($resApp.Count)"
Write-Host "Widget sources: $($swiftWidget.Count), Widget resources: $($resWidget.Count)"
