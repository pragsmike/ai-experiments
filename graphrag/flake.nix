{
  description = "GraphRAG dev-shell – pin 23.11, use PyPI wheel";

  inputs = {
    # 23.11 uses GCC-13 by default, so Triton in the wheel runs fine
    nixpkgs.url     = "github:NixOS/nixpkgs/nixos-23.11";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs   = import nixpkgs { inherit system; };
        python = pkgs.python311;           # GraphRAG + deps are 3.11-ready
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            python
            python.pkgs.pip python.pkgs.setuptools python.pkgs.wheel
            pkgs.stdenv.cc.cc          # brings libstdc++.so.6 for PyTorch/Triton
          ];

          # expose the C++ runtime shipped by gcc13 so binary wheels can load
          LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath [ pkgs.stdenv.cc.cc ];


	shellHook = ''
	  if [ ! -d .venv ]; then
	    echo "⎈  Creating venv and installing GraphRAG…"
	    python -m venv .venv
	    source .venv/bin/activate
	    pip install --upgrade pip
	    pip install "graphrag[all]"

	    # ← ensure PyTorch wheel is pulled in
	    pip install --upgrade --extra-index-url https://download.pytorch.org/whl/cpu torch==2.2.*
	  else
	    source .venv/bin/activate
	  fi
	'';

#          shellHook = ''
#            if [ ! -d .venv ]; then
#              echo "⎈  Creating virtual-env and installing GraphRAG wheel …"
#              python -m venv .venv
#              source .venv/bin/activate
#              pip install --upgrade pip
#              pip install "graphrag[all]"       # wheel from PyPI
#            else
#              source .venv/bin/activate
#            fi
#            echo "✓ GraphRAG environment ready (Python $(python -V | cut -d' ' -f2))"
#          '';
        };
      });
}
