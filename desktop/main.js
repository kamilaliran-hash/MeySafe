// MeySafe desktop shell (Electron) — Developer: Kamil
const { app, BrowserWindow, ipcMain, dialog, shell, Menu } = require("electron");
const path = require("path");
const fs = require("fs");

app.setAppUserModelId("com.kamil.meysafe");
if (!app.requestSingleInstanceLock()) app.quit();

let win;
function createWindow() {
  win = new BrowserWindow({
    width: 1100, height: 780, minWidth: 380, minHeight: 600,
    title: "MeySafe", backgroundColor: "#0b1220",
    icon: path.join(__dirname, "www", "icon.png"),
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true, nodeIntegration: false, sandbox: true,
      devTools: !app.isPackaged, spellcheck: false
    }
  });
  Menu.setApplicationMenu(null);
  win.setContentProtection(true);               // blocks screenshots / screen recording of the vault
  win.loadFile(path.join(__dirname, "www", "index.html"));
  win.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  win.webContents.on("will-navigate", e => e.preventDefault());
  win.on("minimize", () => win.webContents.executeJavaScript("window.msLockNow && cfg.settings.autolock===0 && window.msLockNow()").catch(() => {}));
}

ipcMain.handle("ms:save", async (_e, name, bytes) => {
  const r = await dialog.showSaveDialog(win, { defaultPath: path.join(app.getPath("downloads"), name) });
  if (r.canceled || !r.filePath) throw new Error("cancelled");
  await fs.promises.writeFile(r.filePath, Buffer.from(bytes));
  return r.filePath;
});
ipcMain.handle("ms:pickDir", async () => {
  const r = await dialog.showOpenDialog(win, { properties: ["openDirectory", "createDirectory"], defaultPath: app.getPath("downloads") });
  return r.canceled ? null : r.filePaths[0];
});
ipcMain.handle("ms:writeTo", async (_e, dir, name, bytes) => {
  const safe = String(name).replace(/[\\/:*?"<>|]/g, "_");
  let target = path.join(dir, safe), i = 1;
  const { name: base, ext } = path.parse(safe);
  while (fs.existsSync(target)) target = path.join(dir, `${base} (${i++})${ext}`);
  await fs.promises.writeFile(target, Buffer.from(bytes));
  return dir;
});

app.on("second-instance", () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
app.whenReady().then(createWindow);
app.on("window-all-closed", () => app.quit());
