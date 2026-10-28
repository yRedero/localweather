# Drives a running Minecraft window with real keyboard input (scancodes, which GLFW reads like a keyboard).
# Usage: mcdrive.ps1 -ProcessId 6020 -Steps "tap:F2","wait:300","down:D","wait:120","tap:F2","up:D"
param(
    [int]$ProcessId,
    [string[]]$Steps
)

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class McDrive {
    [StructLayout(LayoutKind.Sequential)] struct KEYBDINPUT { public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }
    [StructLayout(LayoutKind.Sequential)] struct MOUSEINPUT { public int dx; public int dy; public uint mouseData; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }
    [StructLayout(LayoutKind.Explicit)] struct UNION { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }
    [StructLayout(LayoutKind.Sequential)] struct INPUT { public uint type; public UNION u; }
    [DllImport("user32.dll", SetLastError = true)] static extern uint SendInput(uint n, INPUT[] inputs, int size);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int cmd);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    const uint KEYEVENTF_SCANCODE = 0x0008, KEYEVENTF_KEYUP = 0x0002;
    public static void Key(ushort scan, bool up) {
        INPUT[] i = new INPUT[1];
        i[0].type = 1;
        i[0].u.ki.wScan = scan;
        i[0].u.ki.dwFlags = KEYEVENTF_SCANCODE | (up ? KEYEVENTF_KEYUP : 0);
        SendInput(1, i, Marshal.SizeOf(typeof(INPUT)));
    }
}
"@

$scan = @{ "A" = 0x1E; "D" = 0x20; "W" = 0x11; "S" = 0x1F; "F2" = 0x3C; "ESC" = 0x01; "SHIFT" = 0x2A; "F1" = 0x3B }

$proc = Get-Process -Id $ProcessId
$hwnd = $proc.MainWindowHandle
# Windows only lets a background process take the foreground right after keyboard input, hence the Alt tap.
for ($try = 0; $try -lt 5 -and [McDrive]::GetForegroundWindow() -ne $hwnd; $try++) {
    [McDrive]::Key(0x38, $false); [McDrive]::Key(0x38, $true)
    [McDrive]::ShowWindow($hwnd, 9) | Out-Null
    [McDrive]::SetForegroundWindow($hwnd) | Out-Null
    Start-Sleep -Milliseconds 600
}
# Never type into some other window.
if ([McDrive]::GetForegroundWindow() -ne $hwnd) { Write-Output "error: Minecraft window is not in front, nothing sent"; exit 1 }

foreach ($step in $Steps) {
    $kind, $arg = $step.Split(":")
    switch ($kind) {
        "wait" { Start-Sleep -Milliseconds ([int]$arg) }
        "down" { [McDrive]::Key($scan[$arg], $false) }
        "up"   { [McDrive]::Key($scan[$arg], $true) }
        "tap"  { [McDrive]::Key($scan[$arg], $false); Start-Sleep -Milliseconds 30; [McDrive]::Key($scan[$arg], $true) }
    }
}
Write-Output "done"
