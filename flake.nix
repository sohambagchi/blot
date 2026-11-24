{
  description = "Screen Scratch: pen-based notes app for Android (dev shell)";

  inputs = {
    android-emu.url = "git+file:///home/soham/dev/android-emu";
    # Share android-emu's nixpkgs so the emulator SDK is not downloaded twice.
    nixpkgs.follows = "android-emu/nixpkgs";
  };

  outputs = { self, nixpkgs, android-emu }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          android_sdk.accept_license = true;
          allowUnfree = true;
        };
      };
      emu = android-emu.packages.${system};

      # Build SDK for Gradle. Kept separate from android-emu's SDK (which is pinned to
      # platform 35 / build-tools 35 and carries the emulator) because current AGP and
      # AndroidX need a newer compileSdk, and the Nix store SDK is read-only so AGP
      # cannot install what it is missing.
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

      # A virtual device shaped like the Lenovo TB-J616F (2000x1200, landscape-native).
      # Density 240 is an assumption until checked with `adb shell wm density` on the device.
      emu-tablet = pkgs.writeShellApplication {
        name = "emu-tablet";
        runtimeInputs = [ emu.sdk emu.emu-up pkgs.gnused pkgs.coreutils ];
        text = ''
          export EMU_NAME="''${EMU_NAME:-tab-35}"
          export EMU_PORT="''${EMU_PORT:-5556}"
          # avdmanager otherwise falls back to $XDG_CONFIG_HOME/.android, where emu-up won't look.
          export ANDROID_AVD_HOME="''${ANDROID_AVD_HOME:-$HOME/.android/avd}"
          avd_home="$ANDROID_AVD_HOME"
          export ANDROID_SDK_ROOT="${emu.sdk}/libexec/android-sdk"
          export ANDROID_HOME="$ANDROID_SDK_ROOT"
          if [[ ! -f "$avd_home/$EMU_NAME.ini" ]]; then
            echo "Creating AVD $EMU_NAME (2000x1200 @ 240dpi)"
            echo no | avdmanager create avd -n "$EMU_NAME" \
              -k "system-images;android-35;google_apis;x86_64" -d pixel_tablet >/dev/null
            cfg="$avd_home/$EMU_NAME.avd/config.ini"
            set_cfg() { sed -i "/^$1=/d" "$cfg"; echo "$1=$2" >> "$cfg"; }
            set_cfg hw.lcd.width 2000
            set_cfg hw.lcd.height 1200
            set_cfg hw.lcd.density 240
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
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          buildSdk
          pkgs.jdk21
          pkgs.gradle # only to (re)generate the wrapper; builds use ./gradlew
          emu.emu-up emu.emu-down emu.emu-shot
          emu-tablet
        ];
        ANDROID_HOME = sdkRoot;
        ANDROID_SDK_ROOT = sdkRoot;
        JAVA_HOME = "${pkgs.jdk21}";
        # Use the Nix-patched aapt2 instead of the generic-FHS one AGP downloads from Maven.
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/${buildToolsVersion}/aapt2";
      };
    };
}
