{
  description = "Install Docker (CLI+daemon) and Docker‑Compose via `nix profile`";

  inputs = {
    # pin to whatever nixpkgs you like
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.11";
  };

  outputs = { self, nixpkgs, ... }: let
    system = "x86_64-linux";
    pkgs   = import nixpkgs { inherit system; };
  in {
    # ======== user-profile packages ========
    # This creates ~/.nix-profile/bin/{docker,dockerd,docker-compose}
    packages = {
      # only one definition of packages.${system}:
      "${system}" = {
        docker         = pkgs.docker;
        "docker-compose" = pkgs.docker-compose;
	btop		= pkgs.btop;
	jq		= pkgs.jq;
      };
    };

    # ======== apps for `nix run .#docker` etc. ========
    apps = {
      "${system}" = {
        docker = {
          type    = "app";
          program = "${pkgs.docker}/bin/docker";
        };
        "docker-compose" = {
          type    = "app";
          program = "${pkgs.docker-compose}/bin/docker-compose";
        };
        btop = {
          type    = "app";
          program = "${pkgs.btop}/bin/btop";
        };
        jq = {
          type    = "app";
          program = "${pkgs.jq}/bin/jq";
        };
      };
    };
  };
}
