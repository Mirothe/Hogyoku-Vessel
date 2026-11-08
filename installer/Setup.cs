using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Drawing.Text;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;
using Microsoft.Win32;

[assembly: AssemblyTitle("Hogyoku Vessel Setup")]
[assembly: AssemblyProduct("Hogyoku Vessel")]
[assembly: AssemblyCompany("Mirothe")]
[assembly: AssemblyCopyright("Copyright 2026 Mirothe")]

namespace Vessel
{
    static class Setup
    {
        const string Repo = "%REPO%";
        const string AppName = "Hogyoku Vessel";
        const string UninstallKey = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\HogyokuVessel";
        // the app keeps its settings with java's Preferences, which on Windows live under this key
        const string AppPrefsKey = @"Software\JavaSoft\Prefs\dev\mirothe\vessel";
        static readonly string Site = "https://" + Repo.Split('/')[0].ToLowerInvariant() + ".github.io/" + (Repo.Contains("/") ? Repo.Split('/')[1] : "") + "/";

        static string installDir = RegisteredDir() ?? DefaultDir();

        // a release zip given with /zip, or sitting next to the installer, is installed straight from disk without asking GitHub
        static string localZip;

        static Release FromZip(string path)
        {
            var m = System.Text.RegularExpressions.Regex.Match(Path.GetFileName(path), @"(\d+(\.\d+)+)");
            return new Release { Tag = m.Success ? m.Groups[1].Value : "local", Url = path, Size = new FileInfo(path).Length };
        }

        static string ZipBeside()
        {
            string here = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
            string[] found = Directory.GetFiles(here, "Hogyoku-Vessel-*-windows.zip");
            Array.Sort(found);
            return found.Length > 0 ? found[found.Length - 1] : null;
        }

        static string InstallDir
        {
            get { return installDir; }
            set { installDir = value; }
        }

        static string DefaultDir()
        {
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", AppName);
        }

        static string RegisteredDir()
        {
            using (RegistryKey key = Registry.CurrentUser.OpenSubKey(UninstallKey))
            {
                string dir = key == null ? null : key.GetValue("InstallLocation") as string;
                return string.IsNullOrEmpty(dir) ? null : dir;
            }
        }

        enum Mode { Install, Update, Uninstall }

        [STAThread]
        static void Main(string[] args)
        {
            // GitHub refuses the old TLS versions .NET 4 picks by default
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072;
            Application.EnableVisualStyles();
            string first = args.Length > 0 ? args[0] : "";

            if (first == "/render" && args.Length > 1)
            {
                Window.RenderPages(args[1]);
                return;
            }

            // the install swaps out the app folder, and a copy of us lives in there, so run from temp instead
            string self = Assembly.GetExecutingAssembly().Location;
            if (Inside(self))
            {
                string copy = Path.Combine(Path.GetTempPath(), "Hogyoku Vessel Setup " + Guid.NewGuid().ToString("N").Substring(0, 8) + ".exe");
                File.Copy(self, copy, true);
                Process.Start(new ProcessStartInfo(copy, string.Join(" ", args)) { WorkingDirectory = Path.GetTempPath(), UseShellExecute = false });
                return;
            }
            // the app starts us with its own folder as the working directory, and Windows won't delete a folder someone is standing in
            Directory.SetCurrentDirectory(Path.GetTempPath());
            Mode mode = first == "/uninstall" ? Mode.Uninstall : first == "/update" ? Mode.Update : Mode.Install;
            int zipArg = Array.IndexOf(args, "/zip");
            localZip = zipArg >= 0 && zipArg + 1 < args.Length ? Path.GetFullPath(args[zipArg + 1]) : ZipBeside();
            Application.Run(new Window(mode));
        }

        static bool Inside(string path)
        {
            return path.StartsWith(InstallDir + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase);
        }

        static void WaitForApp()
        {
            int me = Process.GetCurrentProcess().Id;
            foreach (var p in Process.GetProcesses())
            {
                try
                {
                    if (p.Id == me || !Inside(p.MainModule.FileName)) continue;
                    if (!p.WaitForExit(10000)) p.Kill();
                    p.WaitForExit(3000);
                }
                catch { }
            }
        }

        // antivirus and the indexer like to hold files for a moment after a program closes, so give it a few tries
        static void Retry(Action action)
        {
            for (int i = 0; ; i++)
            {
                try
                {
                    action();
                    return;
                }
                catch (IOException) { if (i >= 20) throw; }
                catch (UnauthorizedAccessException) { if (i >= 20) throw; }
                Thread.Sleep(500);
            }
        }

        sealed class Release
        {
            public string Tag;
            public string Url;
            public long Size;
        }

        static Release Latest()
        {
            using (var web = new WebClient())
            {
                web.Headers.Add("User-Agent", "HogyokuVesselSetup");
                web.Headers.Add("Accept", "application/vnd.github+json");
                string json = web.DownloadString("https://api.github.com/repos/" + Repo + "/releases/latest");
                var data = new JavaScriptSerializer().Deserialize<Dictionary<string, object>>(json);
                var assets = (System.Collections.ArrayList)data["assets"];
                foreach (Dictionary<string, object> asset in assets)
                {
                    string name = (string)asset["name"];
                    if (name.EndsWith("-windows.zip", StringComparison.OrdinalIgnoreCase))
                    {
                        return new Release
                        {
                            Tag = (string)data["tag_name"],
                            Url = (string)asset["browser_download_url"],
                            Size = Convert.ToInt64(asset["size"])
                        };
                    }
                }
                throw new Exception("The latest release doesn't have a Windows download attached.");
            }
        }

        // the folder picked in the options always gets its own Hogyoku Vessel folder inside it, and an install never
        // replaces a folder that holds something other than an earlier install
        static string FolderFor(string picked)
        {
            string dir = picked.TrimEnd(Path.DirectorySeparatorChar);
            if (!Path.GetFileName(dir).Equals(AppName, StringComparison.OrdinalIgnoreCase)) dir = Path.Combine(dir, AppName);
            return dir;
        }

        static void CheckFolder(string dir)
        {
            if (Directory.Exists(dir) && !File.Exists(Path.Combine(dir, AppName + ".exe")) && Directory.GetFileSystemEntries(dir).Length > 0)
                throw new Exception("\"" + dir + "\" already has other files in it. Pick an empty folder.");
        }

        sealed class Choices
        {
            public bool Desktop = true, StartMenu = true, Discord = true, FirstInstall = true;
        }

        static void Install(Release release, Action<int, string> progress, Choices choices)
        {
            CheckFolder(InstallDir);
            string temp = Path.Combine(Path.GetTempPath(), "hogyoku-vessel-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(temp);
            try
            {
                string zip = Path.Combine(temp, "app.zip");
                if (File.Exists(release.Url))
                {
                    progress(40, "Copying " + Path.GetFileName(release.Url));
                    File.Copy(release.Url, zip);
                    progress(85, "Copied " + Path.GetFileName(release.Url));
                }
                else using (var web = new WebClient())
                {
                    web.Headers.Add("User-Agent", "HogyokuVesselSetup");
                    var done = new ManualResetEvent(false);
                    Exception failed = null;
                    web.DownloadProgressChanged += (s, e) =>
                    {
                        long total = e.TotalBytesToReceive > 0 ? e.TotalBytesToReceive : release.Size;
                        int pct = total > 0 ? (int)(e.BytesReceived * 85 / total) : 0;
                        progress(pct, string.Format("Downloading {0}, {1:0.0} of {2:0.0} MB", release.Tag.TrimStart('v', 'V'), e.BytesReceived / 1048576.0, total / 1048576.0));
                    };
                    web.DownloadFileCompleted += (s, e) =>
                    {
                        failed = e.Error;
                        done.Set();
                    };
                    web.DownloadFileAsync(new Uri(release.Url), zip);
                    done.WaitOne();
                    if (failed != null) throw failed;
                }

                progress(88, "Unpacking the app");
                string unpacked = Path.Combine(temp, "unpacked");
                ZipFile.ExtractToDirectory(zip, unpacked);
                string source = Path.Combine(unpacked, AppName);
                if (!Directory.Exists(source)) source = unpacked;

                progress(90, "Waiting for " + AppName + " to close");
                WaitForApp();
                progress(92, "Installing");
                Directory.CreateDirectory(Path.GetDirectoryName(InstallDir));
                string old = InstallDir + ".old";
                if (Directory.Exists(old)) Retry(() => Directory.Delete(old, true));
                // move the old version aside first, so a failure halfway never leaves you with no app at all
                if (Directory.Exists(InstallDir)) Retry(() => Directory.Move(InstallDir, old));
                try
                {
                    Retry(() => Directory.Move(source, InstallDir));
                }
                catch
                {
                    if (Directory.Exists(old) && !Directory.Exists(InstallDir)) Directory.Move(old, InstallDir);
                    throw;
                }
                try { Directory.Delete(old, true); } catch { }

                // keep a copy of the installer around so Windows can uninstall the app later
                string self = Assembly.GetExecutingAssembly().Location;
                File.Copy(self, Path.Combine(InstallDir, "Setup.exe"), true);

                progress(95, "Adding shortcuts");
                string exe = Path.Combine(InstallDir, AppName + ".exe");
                if (choices.StartMenu) Shortcut(StartMenuLink(), exe);
                if (choices.Desktop) Shortcut(DesktopLink(), exe);
                if (choices.FirstInstall) RememberDiscordChoice(choices.Discord);
                Register(release.Tag, exe);
                progress(100, "Installed " + release.Tag.TrimStart('v', 'V'));
            }
            finally
            {
                try { Directory.Delete(temp, true); } catch { }
            }
        }

        static string StartMenuLink()
        {
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), AppName + ".lnk");
        }

        static string DesktopLink()
        {
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), AppName + ".lnk");
        }

        // written the way java's Preferences stores a boolean, so the app reads it on its very first start
        static void RememberDiscordChoice(bool on)
        {
            using (RegistryKey key = Registry.CurrentUser.CreateSubKey(AppPrefsKey))
            {
                key.SetValue("discord", on ? "true" : "false");
            }
        }

        static void Shortcut(string link, string target)
        {
            Type shellType = Type.GetTypeFromProgID("WScript.Shell");
            object shell = Activator.CreateInstance(shellType);
            object lnk = shellType.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell, new object[] { link });
            Type t = lnk.GetType();
            t.InvokeMember("TargetPath", BindingFlags.SetProperty, null, lnk, new object[] { target });
            t.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, lnk, new object[] { Path.GetDirectoryName(target) });
            t.InvokeMember("IconLocation", BindingFlags.SetProperty, null, lnk, new object[] { target + ",0" });
            t.InvokeMember("Save", BindingFlags.InvokeMethod, null, lnk, null);
        }

        static void Register(string version, string exe)
        {
            using (RegistryKey key = Registry.CurrentUser.CreateSubKey(UninstallKey))
            {
                key.SetValue("DisplayName", AppName);
                key.SetValue("DisplayVersion", version.TrimStart('v', 'V'));
                key.SetValue("Publisher", "Mirothe");
                key.SetValue("DisplayIcon", exe);
                key.SetValue("InstallLocation", InstallDir);
                key.SetValue("UninstallString", "\"" + Path.Combine(InstallDir, "Setup.exe") + "\" /uninstall");
                key.SetValue("NoModify", 1);
                key.SetValue("NoRepair", 1);
            }
        }

        static string InstalledVersion()
        {
            using (RegistryKey key = Registry.CurrentUser.OpenSubKey(UninstallKey))
            {
                return key == null ? null : key.GetValue("DisplayVersion") as string;
            }
        }

        static void Uninstall()
        {
            foreach (var p in Process.GetProcessesByName(AppName))
            {
                try { p.Kill(); p.WaitForExit(3000); } catch { }
            }
            foreach (string link in new[] { StartMenuLink(), DesktopLink() }) if (File.Exists(link)) File.Delete(link);
            Registry.CurrentUser.DeleteSubKeyTree(UninstallKey, false);

            // the copy of us that Windows starts lives inside the folder we want gone, so let cmd finish the job after we exit
            string cmd = "/c timeout /t 2 /nobreak >nul & rmdir /s /q \"" + InstallDir + "\"";
            Process.Start(new ProcessStartInfo("cmd.exe", cmd) { CreateNoWindow = true, UseShellExecute = false });
        }

        sealed class Window : Form
        {
            static readonly Color Dim = Color.FromArgb(140, 140, 140);
            static readonly Color Soft = Color.FromArgb(208, 208, 208);
            static readonly Color Raised = Color.FromArgb(28, 28, 28);
            static readonly Color Line = Color.FromArgb(43, 43, 43);
            static readonly Color Red = Color.FromArgb(255, 110, 110);
            const int Welcome = 0, Terms = 1, Options = 2, Installing = 3, Done = 4, Remove = 5, Removed = 6;

            readonly System.Windows.Forms.Timer tick = new System.Windows.Forms.Timer { Interval = 15 };
            readonly Font titleFont = new Font("Segoe UI Black", 30f);
            readonly Font headFont = new Font("Segoe UI Semibold", 15f);
            readonly Font smallHead = new Font("Segoe UI Semibold", 11f);
            readonly Font textFont = new Font("Segoe UI", 10f);
            readonly Font smallFont = new Font("Segoe UI", 9f);
            readonly Font buttonFont = new Font("Segoe UI Semibold", 10.5f);
            readonly Font bigNumber = new Font("Segoe UI Black", 22f);
            readonly Image gem = LoadGem();
            readonly Mode mode;
            readonly Choices choices = new Choices();
            readonly float[] knobs = { 1f, 1f, 1f };
            Release release;
            string status = "Checking GitHub for the latest version";
            string have;
            bool bad, checking = true, accepted, busy;
            int page;
            float progress, shown, slide = 1f;
            Point mouse = new Point(-1, -1);

            [DllImport("user32.dll")] static extern bool ReleaseCapture();
            [DllImport("user32.dll")] static extern IntPtr SendMessage(IntPtr h, int msg, int w, int l);
            [DllImport("dwmapi.dll")] static extern int DwmSetWindowAttribute(IntPtr h, int attr, ref int value, int size);

            const int W = 640, H = 440;
            Rectangle CloseBox { get { return new Rectangle(W - 46, 0, 46, 40); } }
            Rectangle MinBox { get { return new Rectangle(W - 92, 0, 46, 40); } }
            Rectangle PrimaryBox { get { return new Rectangle(W - 32 - 140, H - 62, 140, 42); } }
            Rectangle BackBox { get { return new Rectangle(W - 32 - 140 - 90, H - 62, 80, 42); } }
            Rectangle TickBox { get { return new Rectangle(32, 306, 22, 22); } }
            Rectangle TermsLink { get { return new Rectangle(141, 304, 92, 24); } }
            Rectangle PrivacyLink { get { return new Rectangle(266, 304, 100, 24); } }
            Rectangle BrowseBox { get { return new Rectangle(W - 32 - 94, 152, 94, 38); } }
            Rectangle ToggleBox(int i) { return new Rectangle(32, 212 + i * 32, 420, 26); }

            static Image LoadGem()
            {
                var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("icon.png");
                return stream == null ? null : Image.FromStream(stream);
            }

            public Window(Mode mode)
            {
                this.mode = mode;
                Text = AppName + " Setup";
                try { Icon = Icon.ExtractAssociatedIcon(Assembly.GetExecutingAssembly().Location); } catch { }
                FormBorderStyle = FormBorderStyle.None;
                ClientSize = new Size(W, H);
                StartPosition = FormStartPosition.CenterScreen;
                BackColor = Color.Black;
                DoubleBuffered = true;
                Opacity = 0;
                choices.FirstInstall = InstalledVersion() == null;
                page = mode == Mode.Uninstall ? Remove : Welcome;

                tick.Tick += (s, e) =>
                {
                    if (Opacity < 1) Opacity = Math.Min(1, Opacity + 0.07);
                    shown += (progress - shown) * 0.12f;
                    slide += (1f - slide) * 0.2f;
                    bool[] want = { choices.Desktop, choices.StartMenu, choices.Discord };
                    for (int i = 0; i < 3; i++) knobs[i] += ((want[i] ? 1f : 0f) - knobs[i]) * 0.25f;
                    Invalidate();
                };
                tick.Start();
                if (mode != Mode.Uninstall) Shown += (s, e) => Check();
            }

            protected override CreateParams CreateParams
            {
                get
                {
                    var p = base.CreateParams;
                    p.ClassStyle |= 0x20000;
                    return p;
                }
            }

            protected override void OnHandleCreated(EventArgs e)
            {
                base.OnHandleCreated(e);
                // Windows 11 rounds the corners for us, older versions just ignore this
                int round = 2;
                try { DwmSetWindowAttribute(Handle, 33, ref round, 4); } catch { }
            }

            void Go(int to)
            {
                page = to;
                slide = 0f;
                bad = false;
            }

            protected override void OnPaint(PaintEventArgs e)
            {
                Draw(e.Graphics);
            }

            void Draw(Graphics g)
            {
                g.SmoothingMode = SmoothingMode.AntiAlias;
                g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;
                g.InterpolationMode = InterpolationMode.HighQualityBicubic;
                g.Clear(Color.Black);
                bool tall = page == Welcome || page == Done || page == Removed;
                Banner(g, tall);
                WindowButtons(g);

                // each page eases in from a little to the right
                var state = g.Save();
                g.TranslateTransform((1f - slide) * 18f, 0);
                switch (page)
                {
                    case Welcome: PaintWelcome(g); break;
                    case Terms: PaintTerms(g); break;
                    case Options: PaintOptions(g); break;
                    case Installing: PaintInstalling(g); break;
                    case Done: PaintDone(g); break;
                    case Remove: PaintRemove(g); break;
                    case Removed: PaintRemoved(g); break;
                }
                g.Restore(state);
                Footer(g);
                using (var edge = new Pen(Color.FromArgb(38, 38, 38))) g.DrawRectangle(edge, 0, 0, W - 1, H - 1);
            }

            // the gem with a soft white glow behind it, big on the first and last page and small on the rest
            void Banner(Graphics g, bool tall)
            {
                int size = tall ? 200 : 34;
                var at = tall ? new Rectangle(W - 250, 46, size, size) : new Rectangle(30, 54, size, size);
                int glow = tall ? 260 : 120;
                using (var path = new GraphicsPath())
                {
                    var c = new Rectangle(at.X + at.Width / 2 - glow, at.Y + at.Height / 2 - glow, glow * 2, glow * 2);
                    path.AddEllipse(c);
                    using (var b = new PathGradientBrush(path))
                    {
                        b.CenterColor = Color.FromArgb(tall ? 42 : 26, 255, 255, 255);
                        b.SurroundColors = new[] { Color.FromArgb(0, 255, 255, 255) };
                        g.FillEllipse(b, c);
                    }
                }
                if (gem != null) g.DrawImage(gem, at);
                if (!tall)
                {
                    TextRenderer.DrawText(g, AppName + " Setup", smallHead, new Point(74, 60), Color.White);
                    using (var p = new Pen(Line)) g.DrawLine(p, 32, 108, W - 32, 108);
                }
            }

            void WindowButtons(Graphics g)
            {
                foreach (var box in new[] { MinBox, CloseBox })
                {
                    bool hot = box.Contains(mouse);
                    if (hot) using (var b = new SolidBrush(box == CloseBox ? Color.FromArgb(196, 43, 28) : Color.FromArgb(40, 40, 40))) g.FillRectangle(b, box);
                    using (var pen = new Pen(hot ? Color.White : Color.FromArgb(200, 200, 200), 1.2f))
                    {
                        int cx = box.X + 23, cy = 20;
                        if (box == CloseBox)
                        {
                            g.DrawLine(pen, cx - 5, cy - 5, cx + 5, cy + 5);
                            g.DrawLine(pen, cx + 5, cy - 5, cx - 5, cy + 5);
                        }
                        else g.DrawLine(pen, cx - 5, cy, cx + 5, cy);
                    }
                }
            }

            void PaintWelcome(Graphics g)
            {
                TextRenderer.DrawText(g, AppName, titleFont, new Point(26, 196), Color.White);
                TextRenderer.DrawText(g, "Write Minecraft shaders and watch them run.", textFont, new Point(34, 254), Soft);
                TextRenderer.DrawText(g, bad ? status : checking ? status : Summary(), textFont,
                    new Rectangle(34, 318, W - 68, 40), bad ? Red : Dim, TextFormatFlags.WordBreak);
            }

            string Summary()
            {
                string want = release.Tag.TrimStart('v', 'V');
                if (have == want) return "You already have " + want + ". You can install it again.";
                if (have != null) return "This updates Hogyoku Vessel from " + have + " to " + want + ".";
                if (File.Exists(release.Url)) return "Version " + want + ", installed from " + Path.GetFileName(release.Url) + " without going online.";
                return "Version " + want + ", a " + (release.Size / 1048576) + " MB download. It takes about a minute.";
            }

            void PaintTerms(Graphics g)
            {
                TextRenderer.DrawText(g, "Terms of use", headFont, new Point(30, 128), Color.White);
                var box = new Rectangle(32, 166, W - 64, 120);
                using (var path = Rounded(box, 10))
                using (var b = new SolidBrush(Color.FromArgb(16, 16, 16))) g.FillPath(b, path);
                string terms = "Hogyoku Vessel is free and open source under the MIT license. Use it for anything, and the shaders and packs "
                    + "you make are yours. It comes with no warranty. When you share packs, Minecraft's own EULA and usage guidelines apply. "
                    + "Some features use GitHub, Discord and catbox.moe, and their terms apply too.";
                TextRenderer.DrawText(g, terms, smallFont, new Rectangle(box.X + 16, box.Y + 14, box.Width - 32, box.Height - 20), Soft, TextFormatFlags.WordBreak);

                var c = TickBox;
                using (var path = Rounded(c, 5))
                {
                    if (accepted) using (var b = new SolidBrush(Color.White)) g.FillPath(b, path);
                    else using (var pen = new Pen(c.Contains(mouse) ? Color.White : Color.FromArgb(90, 90, 90), 1.4f)) g.DrawPath(pen, path);
                }
                if (accepted)
                    using (var pen = new Pen(Color.Black, 2f))
                        g.DrawLines(pen, new[] { new Point(c.X + 5, c.Y + 11), new Point(c.X + 9, c.Y + 15), new Point(c.X + 17, c.Y + 6) });
                TextRenderer.DrawText(g, "I accept the", textFont, new Point(62, 307), Soft);
                Link(g, "Terms of use", TermsLink);
                TextRenderer.DrawText(g, "and", textFont, new Point(234, 307), Soft);
                Link(g, "Privacy Policy", PrivacyLink);
            }

            void Link(Graphics g, string text, Rectangle r)
            {
                bool hot = r.Contains(mouse);
                TextRenderer.DrawText(g, text, textFont, new Point(r.X, r.Y + 3), Color.White);
                var size = TextRenderer.MeasureText(text, textFont);
                using (var pen = new Pen(hot ? Color.White : Color.FromArgb(150, 150, 150))) g.DrawLine(pen, r.X + 4, r.Y + 22, r.X + size.Width - 4, r.Y + 22);
            }

            void PaintOptions(Graphics g)
            {
                TextRenderer.DrawText(g, "Install to", smallFont, new Point(30, 128), Dim);
                var field = new Rectangle(32, 152, W - 64 - 104, 38);
                using (var path = Rounded(field, 10))
                using (var b = new SolidBrush(Raised)) g.FillPath(b, path);
                TextRenderer.DrawText(g, InstallDir, smallFont, new Rectangle(field.X + 12, field.Y, field.Width - 20, field.Height), Color.White,
                    TextFormatFlags.VerticalCenter | TextFormatFlags.PathEllipsis);
                Outline(g, BrowseBox, "Browse");
                string[] labels = { "Desktop shortcut", "Start menu shortcut", "Show what I'm working on in my Discord status" };
                for (int i = 0; i < 3; i++) Toggle(g, ToggleBox(i), labels[i], knobs[i], i < 2 || choices.FirstInstall);
                string note = choices.FirstInstall
                    ? "Shows your project and file name. To show the project's picture, it's uploaded to catbox.moe, a public image host. You can change this any time in Settings."
                    : "You chose this when you first installed. Change it any time in Settings.";
                TextRenderer.DrawText(g, note, smallFont, new Rectangle(78, 300, W - 110, 40), Dim, TextFormatFlags.WordBreak);
            }

            void PaintInstalling(Graphics g)
            {
                TextRenderer.DrawText(g, "Installing", headFont, new Point(30, 140), Color.White);
                TextRenderer.DrawText(g, status, textFont, new Rectangle(32, 222, W - 180, 22), bad ? Red : Soft, TextFormatFlags.EndEllipsis);
                if (!bad)
                {
                    string pct = (int)Math.Round(shown * 100) + "%";
                    var size = TextRenderer.MeasureText(pct, bigNumber);
                    TextRenderer.DrawText(g, pct, bigNumber, new Point(W - 28 - size.Width, 202), Color.White);
                }
                var r = new Rectangle(32, 252, W - 64, 4);
                using (var path = Rounded(r, 2))
                using (var b = new SolidBrush(Raised)) g.FillPath(b, path);
                int fill = (int)(r.Width * Math.Max(0, Math.Min(1, shown)));
                if (fill > 4)
                    using (var path = Rounded(new Rectangle(r.X, r.Y, fill, r.Height), 2))
                    using (var b = new SolidBrush(bad ? Red : Color.White)) g.FillPath(b, path);
                if (bad) TextRenderer.DrawText(g, "Nothing was changed on your PC. Check your connection and try again.", smallFont, new Point(30, 280), Dim);
                else TextRenderer.DrawText(g, "You can keep using your PC while this runs.", smallFont, new Point(30, 280), Dim);
            }

            void PaintDone(Graphics g)
            {
                using (var b = new SolidBrush(Color.White)) g.FillEllipse(b, 32, 150, 36, 36);
                using (var pen = new Pen(Color.Black, 2.6f) { StartCap = LineCap.Round, EndCap = LineCap.Round, LineJoin = LineJoin.Round })
                    g.DrawLines(pen, new[] { new Point(42, 168), new Point(48, 174), new Point(59, 162) });
                TextRenderer.DrawText(g, "You're all set", titleFont, new Point(26, 196), Color.White);
                string want = release == null ? "" : " " + release.Tag.TrimStart('v', 'V');
                TextRenderer.DrawText(g, AppName + want + " is installed. Start with lesson one, or open an example.", textFont, new Point(34, 254), Soft);
            }

            void PaintRemove(Graphics g)
            {
                TextRenderer.DrawText(g, "Remove " + AppName, headFont, new Point(30, 140), Color.White);
                TextRenderer.DrawText(g, bad ? status : "This removes the app and its shortcuts. Your projects, lessons and settings stay where they are.",
                    textFont, new Rectangle(32, 186, W - 64, 60), bad ? Red : Soft, TextFormatFlags.WordBreak);
            }

            void PaintRemoved(Graphics g)
            {
                TextRenderer.DrawText(g, "Removed", titleFont, new Point(26, 196), Color.White);
                TextRenderer.DrawText(g, AppName + " is gone from this PC. Your projects are still where you left them.", textFont, new Point(34, 254), Soft);
            }

            void Footer(Graphics g)
            {
                using (var p = new Pen(Line)) g.DrawLine(p, 32, H - 78, W - 32, H - 78);
                if (page <= Done)
                {
                    for (int i = 0; i < 5; i++)
                        using (var path = Rounded(new Rectangle(32 + i * 22, H - 44, 16, 4), 2))
                        using (var b = new SolidBrush(i <= page ? Color.White : Raised)) g.FillPath(b, path);
                    TextRenderer.DrawText(g, "Step " + (page + 1) + " of 5", smallFont, new Point(148, H - 50), Dim);
                }
                string label = PrimaryLabel();
                bool on = PrimaryOn();
                bool hot = on && PrimaryBox.Contains(mouse);
                using (var path = Rounded(PrimaryBox, 10))
                using (var b = new SolidBrush(!on ? Raised : hot ? Color.FromArgb(222, 222, 222) : Color.White)) g.FillPath(b, path);
                TextRenderer.DrawText(g, label, buttonFont, PrimaryBox, on ? Color.Black : Color.FromArgb(110, 110, 110),
                    TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
                string back = BackLabel();
                if (back != null)
                    TextRenderer.DrawText(g, back, buttonFont, BackBox, BackBox.Contains(mouse) ? Color.White : Dim,
                        TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
            }

            string PrimaryLabel()
            {
                switch (page)
                {
                    case Welcome: return bad ? "Try again" : "Next";
                    case Terms: return "Next";
                    case Options: return have != null && choices.FirstInstall == false ? "Update" : "Install";
                    case Installing: return bad ? "Try again" : "Installing";
                    case Done: return "Launch";
                    case Remove: return busy ? "Removing" : "Remove";
                    default: return "Close";
                }
            }

            bool PrimaryOn()
            {
                switch (page)
                {
                    case Welcome: return !checking;
                    case Terms: return accepted;
                    case Installing: return bad;
                    case Remove: return !busy;
                    default: return true;
                }
            }

            string BackLabel()
            {
                if (page == Terms || page == Options) return "Back";
                if (page == Done || page == Remove) return page == Done ? "Close" : "Cancel";
                return null;
            }

            void Toggle(Graphics g, Rectangle r, string label, float k, bool enabled)
            {
                var track = new Rectangle(r.X, r.Y + 3, 36, 20);
                using (var path = Rounded(track, 10))
                using (var b = new SolidBrush(Mix(Raised, Color.White, enabled ? k : k * 0.5f))) g.FillPath(b, path);
                using (var b = new SolidBrush(Mix(Dim, Color.Black, k))) g.FillEllipse(b, track.X + 3 + 16 * k, track.Y + 3, 14, 14);
                TextRenderer.DrawText(g, label, textFont, new Point(r.X + 46, r.Y + 3), enabled ? Soft : Dim);
            }

            void Outline(Graphics g, Rectangle r, string label)
            {
                using (var path = Rounded(r, 10))
                using (var pen = new Pen(r.Contains(mouse) ? Color.FromArgb(140, 140, 140) : Line)) g.DrawPath(pen, path);
                TextRenderer.DrawText(g, label, buttonFont, r, Color.White, TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
            }

            static Color Mix(Color a, Color b, float t)
            {
                t = Math.Max(0, Math.Min(1, t));
                return Color.FromArgb((int)(a.R + (b.R - a.R) * t), (int)(a.G + (b.G - a.G) * t), (int)(a.B + (b.B - a.B) * t));
            }

            static GraphicsPath Rounded(Rectangle r, int radius)
            {
                var p = new GraphicsPath();
                int d = radius * 2;
                p.AddArc(r.X, r.Y, d, d, 180, 90);
                p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
                p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
                p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
                p.CloseFigure();
                return p;
            }

            bool Clickable(Point p)
            {
                if (CloseBox.Contains(p) || MinBox.Contains(p)) return true;
                if (PrimaryOn() && PrimaryBox.Contains(p)) return true;
                if (BackLabel() != null && BackBox.Contains(p)) return true;
                if (page == Terms && (TickBox.Contains(p) || TermsLink.Contains(p) || PrivacyLink.Contains(p))) return true;
                if (page == Options && BrowseBox.Contains(p)) return true;
                if (page == Options) for (int i = 0; i < 3; i++) if (ToggleBox(i).Contains(p) && (i < 2 || choices.FirstInstall)) return true;
                return false;
            }

            protected override void OnMouseMove(MouseEventArgs e)
            {
                mouse = e.Location;
                Cursor = Clickable(mouse) ? Cursors.Hand : Cursors.Default;
            }

            protected override void OnMouseLeave(EventArgs e)
            {
                mouse = new Point(-1, -1);
            }

            protected override void OnMouseDown(MouseEventArgs e)
            {
                if (e.Button != MouseButtons.Left) return;
                var p = e.Location;
                if (CloseBox.Contains(p)) { if (!busy) Close(); return; }
                if (MinBox.Contains(p)) { WindowState = FormWindowState.Minimized; return; }
                if (PrimaryOn() && PrimaryBox.Contains(p)) { Primary(); return; }
                if (BackLabel() != null && BackBox.Contains(p)) { Back(); return; }
                if (page == Terms)
                {
                    if (TickBox.Contains(p) || new Rectangle(54, 304, 86, 24).Contains(p)) { accepted = !accepted; return; }
                    if (TermsLink.Contains(p)) { Open(Site + "terms.html"); return; }
                    if (PrivacyLink.Contains(p)) { Open(Site + "privacy.html"); return; }
                }
                if (page == Options)
                {
                    if (BrowseBox.Contains(p)) { Browse(); return; }
                    if (ToggleBox(0).Contains(p)) { choices.Desktop = !choices.Desktop; return; }
                    if (ToggleBox(1).Contains(p)) { choices.StartMenu = !choices.StartMenu; return; }
                    if (ToggleBox(2).Contains(p) && choices.FirstInstall) { choices.Discord = !choices.Discord; return; }
                }
                // no title bar, so the whole window works as the drag handle
                ReleaseCapture();
                SendMessage(Handle, 0xA1, 2, 0);
            }

            void Primary()
            {
                switch (page)
                {
                    case Welcome: if (bad) Check(); else Go(Terms); break;
                    case Terms: Go(Options); break;
                    case Options: Start(); break;
                    case Installing: Start(); break;
                    case Done: Launch(); break;
                    case Remove: RemoveApp(); break;
                    default: Close(); break;
                }
            }

            void Back()
            {
                if (page == Terms) Go(Welcome);
                else if (page == Options) Go(Terms);
                else Close();
            }

            static void Open(string url)
            {
                try { Process.Start(new ProcessStartInfo(url) { UseShellExecute = true }); } catch { }
            }

            void Browse()
            {
                using (var dialog = new FolderBrowserDialog { Description = "Pick where Hogyoku Vessel goes. It gets its own folder inside the one you pick.", ShowNewFolderButton = true })
                {
                    string parent = Path.GetDirectoryName(InstallDir);
                    if (Directory.Exists(parent)) dialog.SelectedPath = parent;
                    if (dialog.ShowDialog(this) != DialogResult.OK) return;
                    string dir = FolderFor(dialog.SelectedPath);
                    try
                    {
                        CheckFolder(dir);
                        InstallDir = dir;
                    }
                    catch (Exception ex)
                    {
                        MessageBox.Show(this, ex.Message, AppName, MessageBoxButtons.OK, MessageBoxIcon.Warning);
                    }
                }
            }

            void Check()
            {
                bad = false;
                checking = true;
                status = "Checking GitHub for the latest version";
                new Thread(() =>
                {
                    try
                    {
                        var found = localZip != null && File.Exists(localZip) ? FromZip(localZip) : Latest();
                        string installed = InstalledVersion();
                        Ui(() =>
                        {
                            release = found;
                            have = installed;
                            checking = false;
                            // an update started from inside the app skips the questions and keeps the earlier choices
                            if (mode == Mode.Update && have != release.Tag.TrimStart('v', 'V'))
                            {
                                choices.Desktop = File.Exists(DesktopLink());
                                choices.StartMenu = File.Exists(StartMenuLink());
                                Start();
                            }
                        });
                    }
                    catch (Exception ex)
                    {
                        Ui(() =>
                        {
                            checking = false;
                            bad = true;
                            status = "Couldn't reach GitHub: " + ex.Message;
                        });
                    }
                }) { IsBackground = true }.Start();
            }

            void Start()
            {
                Go(Installing);
                busy = true;
                progress = 0;
                shown = 0;
                status = "Starting the download";
                new Thread(() =>
                {
                    try
                    {
                        Install(release, (pct, text) => Ui(() =>
                        {
                            progress = Math.Max(0, Math.Min(100, pct)) / 100f;
                            status = text;
                        }), choices);
                        Ui(() =>
                        {
                            busy = false;
                            progress = 1;
                            Go(Done);
                            if (mode == Mode.Update) Launch();
                        });
                    }
                    catch (Exception ex)
                    {
                        Ui(() =>
                        {
                            busy = false;
                            bad = true;
                            status = "Install failed: " + ex.Message;
                        });
                    }
                }) { IsBackground = true }.Start();
            }

            void Launch()
            {
                try { Process.Start(new ProcessStartInfo(Path.Combine(InstallDir, AppName + ".exe")) { WorkingDirectory = InstallDir }); } catch { }
                Close();
            }

            void RemoveApp()
            {
                busy = true;
                new Thread(() =>
                {
                    try
                    {
                        Uninstall();
                        Ui(() => { busy = false; Go(Removed); });
                    }
                    catch (Exception ex)
                    {
                        Ui(() => { busy = false; bad = true; status = "Couldn't remove it: " + ex.Message; });
                    }
                }) { IsBackground = true }.Start();
            }

            void Ui(Action a)
            {
                if (IsHandleCreated) BeginInvoke(a);
            }

            // paints every page with sample data into PNGs, so the design can be checked without installing anything
            public static void RenderPages(string dir)
            {
                Directory.CreateDirectory(dir);
                var w = new Window(Mode.Install);
                w.tick.Stop();
                w.release = new Release { Tag = "v1.1.0", Size = 50331648 };
                w.checking = false;
                w.Opacity = 1;
                string[] names = { "1-welcome", "2-terms", "3-options", "4-installing", "5-done", "6-remove", "7-removed" };
                for (int p = 0; p < names.Length; p++)
                {
                    w.page = p;
                    w.accepted = p == Terms ? false : true;
                    w.progress = w.shown = 0.44f;
                    w.status = p == Installing ? "Downloading 1.1.0, 21.4 of 48.0 MB" : w.status;
                    using (var bmp = new Bitmap(W, H))
                    using (var g = Graphics.FromImage(bmp))
                    {
                        w.Draw(g);
                        bmp.Save(Path.Combine(dir, names[p] + ".png"), ImageFormat.Png);
                    }
                }
            }
        }
    }
}
