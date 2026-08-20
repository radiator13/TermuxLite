#include <jni.h>
#include <string>
#include <vector>
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <iomanip>
#include <sstream>

#if defined(__ARM_NEON) || defined(__aarch64__)
#include <arm_neon.h>
#endif

namespace {

// Standard SHA-256 implementation
class Sha256 {
public:
    Sha256() { reset(); }

    void reset() {
        state_[0] = 0x6a09e667;
        state_[1] = 0xbb67ae85;
        state_[2] = 0x3c6ef372;
        state_[3] = 0xa54ff53a;
        state_[4] = 0x510e527f;
        state_[5] = 0x9b05688c;
        state_[6] = 0x1f83d9ab;
        state_[7] = 0x5be0cd19;
        count_ = 0;
        buffer_len_ = 0;
    }

    void update(const uint8_t* data, size_t len) {
        count_ += len;
        if (buffer_len_ > 0) {
            size_t to_fill = 64 - buffer_len_;
            if (len >= to_fill) {
                std::memcpy(buffer_ + buffer_len_, data, to_fill);
                transform(buffer_);
                data += to_fill;
                len -= to_fill;
                buffer_len_ = 0;
            } else {
                std::memcpy(buffer_ + buffer_len_, data, len);
                buffer_len_ += len;
                return;
            }
        }

        while (len >= 64) {
            transform(data);
            data += 64;
            len -= 64;
        }

        if (len > 0) {
            std::memcpy(buffer_, data, len);
            buffer_len_ = len;
        }
    }

    std::string finalize() {
        uint8_t pad[64];
        pad[0] = 0x80;
        size_t pad_len = (buffer_len_ < 56) ? (56 - buffer_len_) : (120 - buffer_len_);
        std::memset(pad + 1, 0, pad_len - 1);
        update(pad, pad_len);

        uint64_t bit_len = count_ * 8;
        uint8_t len_bytes[8];
        for (int i = 7; i >= 0; --i) {
            len_bytes[7 - i] = static_cast<uint8_t>((bit_len >> (i * 8)) & 0xFF);
        }
        update(len_bytes, 8);

        std::ostringstream oss;
        for (int i = 0; i < 8; ++i) {
            oss << std::hex << std::setw(8) << std::setfill('0') << state_[i];
        }
        return oss.str();
    }

private:
    static inline uint32_t rotr(uint32_t x, uint32_t n) {
        return (x >> n) | (x << (32 - n));
    }

    static inline uint32_t ch(uint32_t x, uint32_t y, uint32_t z) {
        return (x & y) ^ (~x & z);
    }

    static inline uint32_t maj(uint32_t x, uint32_t y, uint32_t z) {
        return (x & y) ^ (x & z) ^ (y & z);
    }

    static inline uint32_t sigma0(uint32_t x) {
        return rotr(x, 2) ^ rotr(x, 13) ^ rotr(x, 22);
    }

    static inline uint32_t sigma1(uint32_t x) {
        return rotr(x, 6) ^ rotr(x, 11) ^ rotr(x, 25);
    }

    static inline uint32_t gamma0(uint32_t x) {
        return rotr(x, 7) ^ rotr(x, 18) ^ (x >> 3);
    }

    static inline uint32_t gamma1(uint32_t x) {
        return rotr(x, 17) ^ rotr(x, 19) ^ (x >> 10);
    }

    void transform(const uint8_t* block) {
        static const uint32_t K[64] = {
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
        };

        uint32_t w[64];
        for (int i = 0; i < 16; ++i) {
            w[i] = (static_cast<uint32_t>(block[i * 4]) << 24) |
                   (static_cast<uint32_t>(block[i * 4 + 1]) << 16) |
                   (static_cast<uint32_t>(block[i * 4 + 2]) << 8) |
                   (static_cast<uint32_t>(block[i * 4 + 3]));
        }
        for (int i = 16; i < 64; ++i) {
            w[i] = gamma1(w[i - 2]) + w[i - 7] + gamma0(w[i - 15]) + w[i - 16];
        }

        uint32_t a = state_[0];
        uint32_t b = state_[1];
        uint32_t c = state_[2];
        uint32_t d = state_[3];
        uint32_t e = state_[4];
        uint32_t f = state_[5];
        uint32_t g = state_[6];
        uint32_t h = state_[7];

        for (int i = 0; i < 64; ++i) {
            uint32_t t1 = h + sigma1(e) + ch(e, f, g) + K[i] + w[i];
            uint32_t t2 = sigma0(a) + maj(a, b, c);
            h = g;
            g = f;
            f = e;
            e = d + t1;
            d = c;
            c = b;
            b = a;
            a = t1 + t2;
        }

        state_[0] += a;
        state_[1] += b;
        state_[2] += c;
        state_[3] += d;
        state_[4] += e;
        state_[5] += f;
        state_[6] += g;
        state_[7] += h;
    }

    uint32_t state_[8];
    uint64_t count_;
    uint8_t buffer_[64];
    size_t buffer_len_;
};

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_termux_lite_NativeBridge_nativeSha256File(
    JNIEnv* env,
    jclass /* clazz */,
    jstring file_path_jstr
) {
    if (!file_path_jstr) return nullptr;

    const char* path = env->GetStringUTFChars(file_path_jstr, nullptr);
    if (!path) return nullptr;

    FILE* fp = std::fopen(path, "rb");
    if (!fp) {
        env->ReleaseStringUTFChars(file_path_jstr, path);
        return nullptr;
    }

    Sha256 hasher;
    std::vector<uint8_t> buffer(64 * 1024);
    size_t bytes_read = 0;
    while ((bytes_read = std::fread(buffer.data(), 1, buffer.size(), fp)) > 0) {
        hasher.update(buffer.data(), bytes_read);
    }
    std::fclose(fp);

    std::string hash = hasher.finalize();
    env->ReleaseStringUTFChars(file_path_jstr, path);

    return env->NewStringUTF(hash.c_str());
}
