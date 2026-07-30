#include "Jones/ActivationTimeGuard.h"
#include "Jones/ActivationCrypto.h"
#include <windows.h>
#include <fstream>
#include <sstream>
#include <chrono>
#include <algorithm>

namespace ActivationTimeGuard {

// Monotonic clock using QueryPerformanceCounter
static int64_t GetMonotonicMs() {
    static LARGE_INTEGER freq = { 0 };
    static bool freqInit = false;
    if (!freqInit) {
        QueryPerformanceFrequency(&freq);
        freqInit = true;
    }
    LARGE_INTEGER counter;
    QueryPerformanceCounter(&counter);
    return (int64_t)(counter.QuadPart * 1000LL / freq.QuadPart);
}

static int64_t GetCurrentTimeMs() {
    SYSTEMTIME st;
    GetLocalTime(&st);
    FILETIME ft;
    SystemTimeToFileTime(&st, &ft);
    ULARGE_INTEGER uli;
    uli.LowPart = ft.dwLowDateTime;
    uli.HighPart = ft.dwHighDateTime;
    // FILETIME 单位是 100ns，转 ms
    return (int64_t)(uli.QuadPart / 10000ULL);
}

static std::string GetExeDir() {
    char path[MAX_PATH];
    GetModuleFileNameA(NULL, path, MAX_PATH);
    std::string s(path);
    size_t pos = s.find_last_of("\\/");
    return (pos != std::string::npos) ? s.substr(0, pos) : ".";
}

static std::string GetCachePath(const std::string& serialNumber) {
    auto hash = ActivationCrypto::SHA256Hash((const BYTE*)serialNumber.data(), serialNumber.size());
    std::string hex = ActivationCrypto::SHA256Hex((const BYTE*)serialNumber.data(), serialNumber.size());
    return GetExeDir() + "\\activation_cache\\activation_" + hex + ".dat";
}

struct CacheData {
    std::string serialNumber;
    int64_t lastSystemTime = 0;
    int64_t lastMonotonicMs = 0;
    int64_t expireTimestamp = 0;
};

static bool WriteCache(const std::string& path, const CacheData& cache) {
    std::string dir = path.substr(0, path.find_last_of("\\/"));
    CreateDirectoryA(dir.c_str(), NULL);

    std::ofstream ofs(path, std::ios::binary | std::ios::trunc);
    if (!ofs) return false;

    ofs << "sn=" << cache.serialNumber << "\n";
    ofs << "st=" << cache.lastSystemTime << "\n";
    ofs << "mt=" << cache.lastMonotonicMs << "\n";
    ofs << "et=" << cache.expireTimestamp << "\n";
    return true;
}

static CacheData ReadCache(const std::string& path) {
    CacheData cache;
    std::ifstream ifs(path, std::ios::binary);
    if (!ifs) return cache;

    std::string line;
    while (std::getline(ifs, line)) {
        if (line.substr(0, 3) == "sn=") cache.serialNumber = line.substr(3);
        else if (line.substr(0, 3) == "st=") cache.lastSystemTime = _atoi64(line.substr(3).c_str());
        else if (line.substr(0, 3) == "mt=") cache.lastMonotonicMs = _atoi64(line.substr(3).c_str());
        else if (line.substr(0, 3) == "et=") cache.expireTimestamp = _atoi64(line.substr(3).c_str());
    }
    return cache;
}

int64_t GetTrustedTimestamp(const std::string& serialNumber, int64_t expireTimestamp) {
    std::string cachePath = GetCachePath(serialNumber);

    CacheData cache = ReadCache(cachePath);
    if (cache.serialNumber.empty() || cache.serialNumber != serialNumber) {
        return GetCurrentTimeMs();
    }

    int64_t currentMonotonic = GetMonotonicMs();
    if (currentMonotonic < cache.lastMonotonicMs) {
        return GetCurrentTimeMs();
    }

    int64_t msSinceLast = currentMonotonic - cache.lastMonotonicMs;
    int64_t calculatedTime = cache.lastSystemTime + msSinceLast;
    int64_t systemTime = GetCurrentTimeMs();

    // If calculated time differs from system time by more than 24 hours
    // and calculated time is past expiry, use calculated time
    // (prevents clock rollback to bypass expiry)
    int64_t diff = calculatedTime - systemTime;
    if (diff < 0) diff = -diff;
    if (diff > 86400000LL && calculatedTime > expireTimestamp) {
        return calculatedTime;
    }

    return systemTime;
}

void RecordActivation(const std::string& serialNumber, int64_t expireTimestamp) {
    std::string cachePath = GetCachePath(serialNumber);

    CacheData cache;
    cache.serialNumber = serialNumber;
    cache.lastSystemTime = GetCurrentTimeMs();
    cache.lastMonotonicMs = GetMonotonicMs();
    cache.expireTimestamp = expireTimestamp;

    WriteCache(cachePath, cache);
}

} // namespace ActivationTimeGuard
