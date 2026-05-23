#include "Jones/ActivationAntiDebug.h"
#include <windows.h>
#include <debugapi.h>

namespace ActivationAntiDebug {

bool IsBeingDebugged() {
    // Check 1: IsDebuggerPresent
    if (::IsDebuggerPresent()) return true;

    // Check 2: CheckRemoteDebuggerPresent
    BOOL isDebuggerPresent = FALSE;
    CheckRemoteDebuggerPresent(GetCurrentProcess(), &isDebuggerPresent);
    if (isDebuggerPresent) return true;

    // Check 3: GetTickCount timing check
    // Run 100M empty iterations, should take >10ms normally
    DWORD start = GetTickCount();
    volatile int dummy = 0;
    for (int i = 0; i < 100000000; i++) { dummy++; }
    DWORD end = GetTickCount();
    if (end - start < 10) return true;

    return false;
}

} // namespace ActivationAntiDebug
