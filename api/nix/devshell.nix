{
  perSystem = {pkgs, ...}: {
    shellPackages = with pkgs; [jdk25 maven google-java-format];
  };
}
