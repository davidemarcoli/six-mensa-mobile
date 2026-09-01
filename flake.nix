# Android dev environment for six-mensa-mobile — CLI only, no Android Studio, no emulator.
{
  description = "SIX Mensa Android development environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";

  outputs = { nixpkgs, ... }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" ];
      forAllSystems = nixpkgs.lib.genAttrs systems;

      buildToolsVersion = "37.0.0";
      platformVersion = "37";
    in
    {
      devShells = forAllSystems (system:
        let
          # androidenv reads android_sdk.accept_license from config, so
          # nixpkgs.legacyPackages (which has no config) is not usable here.
          pkgs = import nixpkgs {
            inherit system;
            config = {
              allowUnfree = true;
              android_sdk.accept_license = true;
            };
          };

          androidComposition = pkgs.androidenv.composeAndroidPackages {
            cmdLineToolsVersion = "20.0";
            platformToolsVersion = "37.0.0";
            buildToolsVersions = [ buildToolsVersion ];
            platformVersions = [ platformVersion ];

            includeEmulator = false;
            includeSystemImages = false;
            includeNDK = false;
            includeSources = false;
            # Defaults to true on x86_64 and pulls ~200MB we never use.
            includeCmake = false;
          };

          # Note: libexec, not share. See compose-android-packages.nix.
          sdk = "${androidComposition.androidsdk}/libexec/android-sdk";
        in
        {
          default = pkgs.mkShell {
            packages = with pkgs; [
              jdk17 # AGP 8.13 requires 17; jdk25 would need Gradle >= 9.1
              gradle # 8.14.4 — only to bootstrap ./gradlew
              androidComposition.androidsdk
              kotlin-language-server
            ];

            JAVA_HOME = "${pkgs.jdk17}";
            ANDROID_HOME = sdk;
            ANDROID_SDK_ROOT = sdk;

            # Force AGP to use the patched aapt2 from the store instead of the
            # dynamically-linked one it downloads from Maven, which cannot run
            # on NixOS. Verify with:
            #   ./gradlew :app:assembleDebug --info 2>&1 | grep -i aapt2
            GRADLE_OPTS =
              "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/${buildToolsVersion}/aapt2";

            shellHook = ''
              export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

              # Gitignored. Keeps any tool that ignores the env agreeing with it.
              if [ -f settings.gradle.kts ]; then
                printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
              fi
            '';
          };
        });
    };
}
