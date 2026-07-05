package com.kb.infra.util;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

@Slf4j
@Component
public class SshUtil {

    @Data
    public static class SshResult {
        private int exitCode;
        private String stdout;
        private String stderr;

        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    public SshResult execute(String host, int port, String username, String password,
                             String script, int timeoutSeconds) {
        Session session = null;
        ChannelExec channel = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, host, port);
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("PreferredAuthentications", "password,keyboard-interactive,publickey");
            session.setConfig(config);
            session.setTimeout(timeoutSeconds * 1000);
            session.connect();

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(script);

            ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrStream = new ByteArrayOutputStream();
            channel.setOutputStream(stdoutStream);
            channel.setExtOutputStream(stderrStream);

            channel.connect();

            long start = System.currentTimeMillis();
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() - start > timeoutSeconds * 1000L) {
                    channel.disconnect();
                    SshResult r = new SshResult();
                    r.setExitCode(-1);
                    r.setStdout(stdoutStream.toString());
                    r.setStderr("执行超时 (>" + timeoutSeconds + "s)");
                    return r;
                }
                Thread.sleep(100);
            }

            SshResult result = new SshResult();
            result.setExitCode(channel.getExitStatus());
            result.setStdout(stdoutStream.toString().trim());
            result.setStderr(stderrStream.toString().trim());
            return result;
        } catch (Exception e) {
            SshResult result = new SshResult();
            result.setExitCode(-1);
            result.setStderr(e.getMessage());
            log.error("SSH执行失败: {}@{}:{} - {}", username, host, port, e.getMessage());
            return result;
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }
}
