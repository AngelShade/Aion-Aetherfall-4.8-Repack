using System.IO;
using System.Web.Script.Serialization;
using System.Windows.Controls;

namespace Aetherfall {
 public sealed partial class Launcher {
  LanguageSelector languageSelector;
  void LoadLanguages() {
   if(languageSelector==null) {
    languageSelector=new LanguageSelector(root,false);
    Find<ContentControl>("LanguageSelectionHost").Content=languageSelector.View;
    languageSelector.Saved+=ReadLanguagePreferences;
   } else languageSelector.Refresh(false);
  }
  void ReadLanguagePreferences() {
   var values=LanguageSelection.Preferences(root);
   preferences.TextLanguage=LanguageSelection.Selected(values,"TextLanguage");
   preferences.VoiceLanguage=LanguageSelection.Selected(values,"VoiceLanguage");
  }
 }
}
