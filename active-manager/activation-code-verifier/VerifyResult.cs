namespace Jones.Activation
{
    public class VerifyResult
    {
        public bool Success { get; }
        public string Message { get; }
        public string SerialNumber { get; }
        public string DeviceId { get; }
        public long ExpireTimestamp { get; }
        public bool Expired { get; }
        public bool DeviceMismatch { get; }

        private VerifyResult(bool success, string message, string serialNumber,
                            string deviceId, long expireTimestamp, bool expired, bool deviceMismatch)
        {
            Success = success;
            Message = message;
            SerialNumber = serialNumber;
            DeviceId = deviceId;
            ExpireTimestamp = expireTimestamp;
            Expired = expired;
            DeviceMismatch = deviceMismatch;
        }

        public static VerifyResult Ok(string serialNumber, string deviceId, long expireTimestamp)
        {
            return new VerifyResult(true, "验证成功", serialNumber, deviceId, expireTimestamp, false, false);
        }

        public static VerifyResult Fail(string message)
        {
            return new VerifyResult(false, message, null, null, 0, false, false);
        }

        public static VerifyResult Fail(string message, string serialNumber, string deviceId,
                                       long expireTimestamp, bool deviceMismatch)
        {
            return new VerifyResult(false, message, serialNumber, deviceId, expireTimestamp, !deviceMismatch, deviceMismatch);
        }
    }
}
