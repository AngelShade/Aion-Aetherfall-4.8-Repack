using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Markup;

namespace Aetherfall {
 public sealed class LanguageSelector {
  public readonly FrameworkElement View;
  public ComboBox TextChoice { get { return (ComboBox)View.FindName("SelectedText"); } }
  public ComboBox VoiceChoice { get { return (ComboBox)View.FindName("SelectedVoice"); } }
  public Button ApplyButton { get { return (Button)View.FindName("ApplySelection"); } }
  public TextBlock Feedback { get { return (TextBlock)View.FindName("SelectionFeedback"); } }
  public event Action Saved;
  readonly string root;
  bool refreshing;
  public static void Theme(Window window) {
   using(var stream=Assembly.GetExecutingAssembly().GetManifestResourceStream("Aetherfall.LanguageTheme.xaml"))window.Resources.MergedDictionaries.Add((ResourceDictionary)XamlReader.Load(stream));
  }
  public LanguageSelector(string client,bool hub) {
   root=client;
   using(var stream=Assembly.GetExecutingAssembly().GetManifestResourceStream("Aetherfall.LanguageSelector.xaml"))View=(FrameworkElement)XamlReader.Load(stream);
   ((TextBlock)View.FindName("SelectionHint")).Text=hub?"Install extra packs from the library. Text and voices can be mixed.":"Text and voices can be mixed. Choices apply on your next Play.";
   ApplyButton.Click+=delegate { Apply(); };
   Refresh(false);
   TextChoice.SelectionChanged+=delegate { if(!refreshing)Apply(); };
   VoiceChoice.SelectionChanged+=delegate { if(!refreshing)Apply(); };
  }
  public void SetEnabled(bool enabled) { TextChoice.IsEnabled=VoiceChoice.IsEnabled=ApplyButton.IsEnabled=enabled; }
  public void Refresh(bool preserve) {
   string text=null,voice=null;
   if(preserve) { text=TextChoice.SelectedItem==null?null:((LanguageOption)TextChoice.SelectedItem).Code;voice=VoiceChoice.SelectedItem==null?null:((LanguageOption)VoiceChoice.SelectedItem).Code; }
   Dictionary<string,object> preferences;
   try { preferences=LanguageSelection.Preferences(root); }
   catch(Exception error) { preferences=new Dictionary<string,object>();Feedback.Text="Saved settings need attention: "+error.Message; }
   List<LanguageDownload> packs;
   try { packs=LanguageDownloads.Catalog(root); }
   catch(Exception error) { packs=new List<LanguageDownload>();Feedback.Text="Pack library needs attention: "+error.Message; }
   refreshing=true;
   try { foreach(bool voices in new[]{false,true}) {
    ComboBox choice=voices?VoiceChoice:TextChoice;
    string selected=(voices?voice:text)??LanguageSelection.Selected(preferences,voices?"VoiceLanguage":"TextLanguage");
    var options=LanguageSelection.Options(root,voices).Where(x=>x.Available||packs.Any(p=>p.code==x.Code&&p.kind==(voices?"voice":"text"))).ToList();
    choice.ItemsSource=options;choice.SelectedItem=options.FirstOrDefault(x=>x.Available&&x.Code==selected)??options.FirstOrDefault(x=>x.Code=="enu"&&x.Available);
   } } finally { refreshing=false; }
  }
  public void Apply() {
   try {
    var text=TextChoice.SelectedItem as LanguageOption;var voice=VoiceChoice.SelectedItem as LanguageOption;
    if(text==null||voice==null)throw new InvalidOperationException("Choose an installed text and voice language first.");
    LanguageSelection.Validate(root,text.Code,voice.Code);
    LanguageSelection.SaveFields(root,new Dictionary<string,object> { {"TextLanguage",text.Code},{"VoiceLanguage",voice.Code} });
    Feedback.Text="Saved for your next Play.";if(Saved!=null)Saved();
   } catch(Exception error) { Feedback.Text=error.Message; }
  }
 }
}
