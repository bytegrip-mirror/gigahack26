{
  perSystem = {pkgs, ...}: {
    shellPackages = with pkgs; [nodejs_22 prettier];
  };
}
