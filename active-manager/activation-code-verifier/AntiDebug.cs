using System;
using System.Diagnostics;
using System.Runtime.InteropServices;

namespace Jones.Activation
{
    internal static class AntiDebug
    {
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool CheckRemoteDebuggerPresent(IntPtr hProcess, ref bool isDebuggerPresent);

        [DllImport("kernel32.dll")]
        private static extern uint GetTickCount();

        public static bool IsBeingDebugged()
        {
            if (Debugger.IsAttached)
                return true;

            bool isDebuggerPresent = false;
            CheckRemoteDebuggerPresent(Process.GetCurrentProcess().Handle, ref isDebuggerPresent);
            if (isDebuggerPresent)
                return true;

            uint start = GetTickCount();
            for (int i = 0; i < 100000000; i++) { }
            uint end = GetTickCount();

            if (end - start < 10)
                return true;

            return false;
        }
    }
}
