using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;

namespace Aetherfall {
 public static partial class Languages {
  const string LoginNoticeLayout = "ui/loginnotice.xml";
  const string LoginNoticeMember = "aetherfall/login-notice.json";
  const int MaxLoginNoticeBytes = 16 * 1024;

  static byte[] LocalizeLoginNotice(byte[] current, byte[] catalog) {
   if (catalog.Length > MaxLoginNoticeBytes)
    throw new InvalidDataException("The login notice translation catalog is too large.");

   Dictionary<string, object> entry;
   try {
    entry = Json.Deserialize<Dictionary<string, object>>(Utf8.GetString(catalog));
   } catch (Exception error) {
    if (!(error is ArgumentException || error is InvalidOperationException)) throw;
    throw new InvalidDataException("Invalid login notice translation catalog.", error);
   }
   object hashValue, textValue;
   if (entry == null || entry.Count != 2 ||
       !entry.TryGetValue("englishSha256", out hashValue) || !(hashValue is string) ||
       !Regex.IsMatch((string)hashValue, @"\A[0-9a-f]{64}\z") ||
       !entry.TryGetValue("text", out textValue) || !(textValue is string) ||
       String.IsNullOrWhiteSpace((string)textValue))
    throw new InvalidDataException("The login notice catalog requires englishSha256 and text.");

   string translated = (string)textValue;
   if (translated.Any(ch => ch < ' ' && ch != '\t' && ch != '\r' && ch != '\n'))
    throw new InvalidDataException("The login notice translation contains invalid control characters.");

   var layout = QuestBinaryXml.Decode(current);
   var contents = layout.Element("Contents");
   if (layout.Name != "LoginNotice" || contents == null || contents.HasElements ||
       contents.ElementsAfterSelf("Contents").Any() ||
       CustomQuests.Hash(Utf8.GetBytes(contents.Value)) != (string)hashValue)
    return null;

   if (!SameDialogueMarkup(contents.Value, translated) ||
       DialogueMarkup.Replace(translated, "").IndexOfAny(new[] { '<', '>' }) >= 0)
    throw new InvalidDataException("The login notice translation changed its native HTML or placeholders.");
   contents.Value = translated;
   return QuestBinaryXml.Encode(layout);
  }
 }
}
