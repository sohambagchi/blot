{
  description = "Blot: pen-based notes app for Android (dev shell)";

  inputs = {
    android-emu.url = "git+file:///home/soham/dev/android-emu?ref=main";
    # depot: the update client and depot-publish; its committed main, not the working tree.
    depot.url = "git+file:///home/soham/dev/depot?ref=main";
    depot.inputs.nixpkgs.follows = "android-emu/nixpkgs";
    # Share android-emu's nixpkgs so the emulator SDK is not downloaded twice.
    nixpkgs.follows = "android-emu/nixpkgs";
  };

  outputs = { self, nixpkgs, android-emu, depot }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          android_sdk.accept_license = true;
          allowUnfree = true;
        };
      };
      # emu-up defaults to 35 (phone); emu-tablet to 31, the TB-J616F's Android 12. EMU_API overrides.
      emu = android-emu.lib.mkEmu { inherit system; apiLevels = [ "35" "31" ]; };

      # Gradle's SDK, separate from android-emu's emulator SDK; see
      # docs/adr/2026-11-24-1551-separate-build-sdk-from-emulator-sdk.md
      # Both versions must match compileSdk and buildToolsVersion in app/build.gradle.kts.
      buildToolsVersion = "37.0.0";
      buildSdk = (pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "37.2" ];
        buildToolsVersions = [ buildToolsVersion ];
        includeEmulator = false;
        includeSystemImages = false;
        includeNDK = false;
        includeCmake = false;
      }).androidsdk;
      sdkRoot = "${buildSdk}/libexec/android-sdk";

      # Boots an AVD shaped like the Lenovo TB-J616F: 2000x1200 px at 204 dpi (1569x941 dp),
      # landscape-native; see docs/analysis-artifacts/2026-11-24-1905-tb-j616f-measurements.md
      emu-tablet = pkgs.writeShellApplication {
        name = "emu-tablet";
        runtimeInputs = [ emu.sdk emu.emu-up pkgs.gnused pkgs.coreutils ];
        text = ''
          export EMU_API="''${EMU_API:-31}"
          export EMU_NAME="''${EMU_NAME:-tab-$EMU_API}"
          export EMU_PORT="''${EMU_PORT:-5556}"
          # avdmanager otherwise falls back to $XDG_CONFIG_HOME/.android, where emu-up won't look.
          export ANDROID_AVD_HOME="''${ANDROID_AVD_HOME:-$HOME/.android/avd}"
          avd_home="$ANDROID_AVD_HOME"
          export ANDROID_SDK_ROOT="${emu.sdk}/libexec/android-sdk"
          export ANDROID_HOME="$ANDROID_SDK_ROOT"
          if [[ ! -f "$avd_home/$EMU_NAME.ini" ]]; then
            echo "Creating AVD $EMU_NAME (API $EMU_API, 2000x1200 @ 204dpi)"
            echo no | avdmanager create avd -n "$EMU_NAME" \
              -k "system-images;android-$EMU_API;google_apis;x86_64" -d pixel_tablet >/dev/null
            cfg="$avd_home/$EMU_NAME.avd/config.ini"
            set_cfg() { sed -i "/^$1=/d" "$cfg"; echo "$1=$2" >> "$cfg"; }
            set_cfg hw.lcd.width 2000
            set_cfg hw.lcd.height 1200
            set_cfg hw.lcd.density 204
            set_cfg hw.initialOrientation landscape
            set_cfg hw.keyboard yes
            set_cfg hw.ramSize 3072
            set_cfg disk.dataPartition.size 6G
            set_cfg hw.gpu.enabled yes
            set_cfg hw.gpu.mode swiftshader_indirect
          fi
          exec emu-up "$@"
        '';
      };
    in {
      # depotEnv builds depot's update client into the app (settings.gradle.kts) and points
      # depot-publish at the server; see ~/dev/depot/README.md.
      devShells.${system}.default = pkgs.mkShell ({
        packages = [
          buildSdk
          pkgs.jdk21
          emu.emu-up emu.emu-down emu.emu-shot depot.packages.${system}.depot-publish
          emu-tablet
        ];
        ANDROID_HOME = sdkRoot;
        ANDROID_SDK_ROOT = sdkRoot;
        JAVA_HOME = "${pkgs.jdk21}";
        # Use the Nix-patched aapt2 instead of the generic-FHS one AGP downloads from Maven.
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/${buildToolsVersion}/aapt2";
      } // depot.lib.depotEnv);
    };
}
