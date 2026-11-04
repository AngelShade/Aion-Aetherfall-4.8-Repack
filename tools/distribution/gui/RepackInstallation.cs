using System;
using System.IO;

namespace AetherfallSetup {
 // Shared by the Hub and its embedded updater. Check only essential base files;
 // version metadata and the changed files in a patch do not constitute a game.
 public static class RepackInstallation {
  public static readonly string[] RequiredFiles = {
   "REPACK.json", "release.json", "Client/Aetherfall Launcher.exe",
   "Client/bin64/aion.bin", "Client/Data/Items/Items.pak",
   "Client/Data/World/World.pak", "Client/Textures/Textures.pak",
   "Client/Mods/Launcher/Assets/atreia.png", "Client/Mods/Launcher/Assets/frame.png",
   "Control/Database-Common.ps1", "Control/Start-Database.ps1", "Control/Start-Servers.ps1",
   "Server/game-server/libs/game-server-4.8-SNAPSHOT.jar"
  };

  public static bool IsPatchPayload(string path) {
   if (String.IsNullOrWhiteSpace(path)) return false;
   var directory = new DirectoryInfo(path);
   return directory.Name.Equals("payload", StringComparison.OrdinalIgnoreCase) && directory.Parent != null &&
    (File.Exists(Path.Combine(directory.Parent.FullName, "patch-manifest.json")) ||
     directory.Parent.Name.StartsWith("Aetherfall-Patch-", StringComparison.OrdinalIgnoreCase));
  }

  public static string Problem(string path) {
   if (IsPatchPayload(path))
    return "This is an extracted update patch. Select Use Existing Repack for a complete installation, or click INSTALL to install the full base in a separate folder. Your current files are kept.";
   if (String.IsNullOrWhiteSpace(path)) return "Select an Aetherfall installation first.";
   foreach (string relative in RequiredFiles) {
    string file = Path.Combine(path, relative);
    if (!File.Exists(file) || new FileInfo(file).Length == 0)
     return "The full repack is incomplete: missing " + relative + ". Use Existing Repack to select a complete installation, or choose an empty install folder and click INSTALL. Your current files are kept.";
   }
   return null;
  }

  public static bool IsComplete(string path) { return Problem(path) == null; }
 }
}
