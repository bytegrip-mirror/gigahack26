{
  perSystem = {
    pkgs,
    lib,
    config,
    ...
  }: {
    options.shellPackages = lib.mkOption {
      type = lib.types.listOf lib.types.package;
      default = [];
    };
    config.devShells.default = pkgs.mkShell {
      name = "gigahack26";
      packages = lib.unique config.shellPackages;
    };
  };
}
