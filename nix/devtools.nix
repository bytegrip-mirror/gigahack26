{
  perSystem = {pkgs, ...}: {
    shellPackages = with pkgs; [just alejandra rumdl docker-client docker-compose];
  };
}
