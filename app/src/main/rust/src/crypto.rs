use sha2::Digest;
use std::fs::File;
use std::io::{Read, BufReader};

pub fn sha256_bytes(bytes: &[u8]) -> String {
    hex_lower(sha2::Sha256::digest(bytes).as_ref())
}

pub fn sha256_file(path: &str) -> Option<String> {
    let file = File::open(path).ok()?;
    let mut reader = BufReader::with_capacity(64 * 1024, file);
    let mut hasher = sha2::Sha256::new();
    let mut buffer = [0u8; 64 * 1024];

    loop {
        match reader.read(&mut buffer) {
            Ok(0) => break,
            Ok(n) => hasher.update(&buffer[..n]),
            Err(_) => return None,
        }
    }

    Some(hex_lower(hasher.finalize().as_ref()))
}

fn hex_lower(bytes: &[u8]) -> String {
    use std::fmt::Write;
    let mut hex = String::with_capacity(bytes.len() * 2);
    for b in bytes {
        let _ = write!(hex, "{:02x}", b);
    }
    hex
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn test_sha256_empty() {
        assert_eq!(
            sha256_bytes(b""),
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        );
    }

    #[test]
    fn test_sha256_abc() {
        assert_eq!(
            sha256_bytes(b"abc"),
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
    }

    #[test]
    fn test_sha256_file() {
        let mut tmp = tempfile_simple("test_sha256.txt");
        tmp.write_all(b"TermuxLite native optimization test\n").unwrap();
        tmp.flush().unwrap();
        let hash = sha256_file("test_sha256.txt");
        assert!(hash.is_some());
        assert_eq!(hash.unwrap().len(), 64);
        let _ = std::fs::remove_file("test_sha256.txt");
    }

    fn tempfile_simple(name: &str) -> File {
        File::create(name).unwrap()
    }
}
