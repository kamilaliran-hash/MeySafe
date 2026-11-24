const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("meysafeDesktop", {
  save: (name, bytes) => ipcRenderer.invoke("ms:save", name, bytes),
  pickDir: () => ipcRenderer.invoke("ms:pickDir"),
  writeTo: (dir, name, bytes) => ipcRenderer.invoke("ms:writeTo", dir, name, bytes)
});
