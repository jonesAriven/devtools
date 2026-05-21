use std::io::Read;

use base64::Engine;
use base45::{decode as base45_decode, encode as base45_encode};
use flate2::read::GzDecoder;

/// Brotli + Base45 压缩，返回 "B5:" 前缀
pub fn compress_text(text: &str) -> String {
    let bytes = text.as_bytes();
    let mut compressor = brotli::CompressorReader::new(bytes, 4096, 11, 22);
    let mut compressed = Vec::new();
    if compressor.read_to_end(&mut compressed).is_err() {
        return text.to_string();
    }
    let encoded = base45_encode(&compressed);
    format!("B5:{}", encoded)
}

/// 解压 Brotli+Base45 或 GZip+Base64 编码的文本
pub fn decompress_text(text: &str) -> Option<String> {
    if let Some(data) = text.strip_prefix("B5:") {
        decompress_b5(data)
    } else if let Some(data) = text.strip_prefix("GZ:") {
        decompress_gz(data)
    } else {
        None
    }
}

fn decompress_b5(data: &str) -> Option<String> {
    let compressed = base45_decode(data).ok()?;
    let mut decoder = brotli::Decompressor::new(compressed.as_slice(), 4096);
    let mut result = String::new();
    decoder.read_to_string(&mut result).ok()?;
    Some(result)
}

fn decompress_gz(data: &str) -> Option<String> {
    let compressed = base64::engine::general_purpose::STANDARD.decode(data).ok()?;
    let mut decoder = GzDecoder::new(compressed.as_slice());
    let mut result = String::new();
    decoder.read_to_string(&mut result).ok()?;
    Some(result)
}
