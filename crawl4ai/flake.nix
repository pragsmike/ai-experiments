{
  description = "Dev shell for Crawl4AI";

  inputs = {
    nixpkgs.url      = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url  = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs    = import nixpkgs { inherit system; };
        python  = pkgs.python311;      # pick your preferred minor
        # Library path that contains libstdc++.so.6
        cppLibPath = pkgs.lib.makeLibraryPath [ pkgs.stdenv.cc.cc ];
      in {
        devShells.default = pkgs.mkShell {
          # Get Python, pip & build tools
          packages = [
            python
            python.pkgs.pip
            python.pkgs.setuptools
            python.pkgs.wheel
	    pkgs.stdenv.cc.cc          
	    pkgs.playwright-driver.browsers
	  ];
	  # make chromium from playwright-driver visible to Playwright
	  PLAYWRIGHT_BROWSERS_PATH          = "${pkgs.playwright-driver.browsers}";
	  PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS = "true";

          # Expose libstdc++.so.6 at run-time
          LD_LIBRARY_PATH = cppLibPath;

          # One-liner to create/enter a venv the first time
          shellHook = ''
            if [ ! -d .venv ]; then
              echo "Setting up virtualenv and installing Crawl4AI…"
              python -m venv .venv
              source .venv/bin/activate
              pip install --upgrade pip
              pip install crawl4ai==0.6.3
            else
              source .venv/bin/activate
            fi
          '';
        };
      });
}
