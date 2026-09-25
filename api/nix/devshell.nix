{
  perSystem = {pkgs, ...}: {
    shellPackages = with pkgs; [jdk25 gradle google-java-format];
    shellHooks = [''ln -sfn ${pkgs.jdk25.home} "$PWD/.jdk"''];
  };
}
