namespace Jones.Activation
{
    public class VerifyResult
    {
        public bool Success { get; }
        public string SerialNumber { get; }
        public string DeviceId { get; }
        public long ExpireTimestamp { get; }
        public bool Expired { get; }
        public bool DeviceMismatch { get; }

        private VerifyResult(bool success, string serialNumber,
                            string deviceId, long expireTimestamp, bool expired, bool deviceMismatch)
        {
            Success = success;
            SerialNumber = serialNumber;
            DeviceId = deviceId;
            ExpireTimestamp = expireTimestamp;
            Expired = expired;
            DeviceMismatch = deviceMismatch;
        }

        public static VerifyResult Ok(string serialNumber, string deviceId, long expireTimestamp)
        {
            return new VerifyResult(true, serialNumber, deviceId, expireTimestamp, false, false);
        }

        public static VerifyResult Fail()
        {
            return new VerifyResult(false, null, null, 0, false, false);
        }

        public static VerifyResult FailExpired(string serialNumber, string deviceId, long expireTimestamp)
        {
            return new VerifyResult(false, serialNumber, deviceId, expireTimestamp, true, false);
        }

        public static VerifyResult FailDeviceMismatch(string serialNumber, string deviceId, long expireTimestamp)
        {
            return new VerifyResult(false, serialNumber, deviceId, expireTimestamp, false, true);
        }
    }
}
