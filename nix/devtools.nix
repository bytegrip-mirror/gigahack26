{
  perSystem = {pkgs, ...}: {
    shellPackages = with pkgs; [alejandra rumdl docker-client docker-compose];
  };
}
