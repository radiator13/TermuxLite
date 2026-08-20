#include <jni.h>
#include <string>
#include <string_view>
#include <vector>
#include <algorithm>
#include <cctype>

namespace {

struct UrlSpan {
    int start;
    int end;
    std::string url;
};

bool isWhitespaceOrDelimiter(char c) {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '<' || c == '>' ||
           c == '\"' || c == '\'' || c == ']' || c == '}' || c == '^' || c == '`';
}

bool isTrailingPunct(char c) {
    return c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?' ||
           c == ')' || c == ']' || c == '}' || c == '>' || c == '\"' || c == '\'';
}

bool isLeadingPunct(char c) {
    return c == '(' || c == '[' || c == '{' || c == '\"' || c == '\'' || c == '<';
}

std::string normalizeUrl(std::string_view raw) {
    // Trim leading
    while (!raw.empty() && isLeadingPunct(raw.front())) {
        raw.remove_prefix(1);
    }
    // Trim trailing
    while (!raw.empty() && isTrailingPunct(raw.back())) {
        raw.remove_suffix(1);
    }

    if (raw.length() < 4) return "";

    std::string token(raw);
    std::string lower = token;
    std::transform(lower.begin(), lower.end(), lower.begin(), [](unsigned char c) { return std::tolower(c); });

    if (lower.rfind("https://", 0) == 0 ||
        lower.rfind("http://", 0) == 0 ||
        lower.rfind("ftp://", 0) == 0 ||
        lower.rfind("ftps://", 0) == 0 ||
        lower.rfind("file://", 0) == 0 ||
        lower.rfind("mailto:", 0) == 0) {
        if ((lower.rfind("http://", 0) == 0 || lower.rfind("https://", 0) == 0) && token.length() < 10) {
            return "";
        }
        return token;
    }

    if (lower.rfind("www.", 0) == 0) {
        return "https://" + token;
    }

    if (lower.rfind("github.com/", 0) == 0 ||
        lower.rfind("gitlab.com/", 0) == 0 ||
        lower.rfind("bitbucket.com/", 0) == 0 ||
        lower.rfind("x.com/", 0) == 0 ||
        lower.rfind("twitter.com/", 0) == 0) {
        return "https://" + token;
    }

    if (token.find("://") != std::string::npos) {
        return token;
    }

    return "";
}

int distanceToHit(int i, const UrlSpan& hit) {
    if (i < hit.start) return hit.start - i;
    if (i >= hit.end) return i - (hit.end - 1);
    return 0;
}

std::vector<UrlSpan> scanLine(std::string_view line) {
    std::vector<UrlSpan> hits;
    const size_t n = line.length();
    size_t i = 0;

    auto addHit = [&](int start, int end, std::string_view raw) {
        std::string norm = normalizeUrl(raw);
        if (norm.empty()) return;
        for (const auto& h : hits) {
            if (start < h.end && end > h.start && h.url == norm) return;
        }
        hits.push_back({start, end, std::move(norm)});
    };

    // 1. Markdown link check: [label](https://url)
    while (i < n) {
        if (line[i] == '[') {
            size_t closeBracket = line.find(']', i + 1);
            if (closeBracket != std::string_view::npos && closeBracket + 1 < n && line[closeBracket + 1] == '(') {
                size_t closeParen = line.find(')', closeBracket + 2);
                if (closeParen != std::string_view::npos) {
                    std::string_view urlPart = line.substr(closeBracket + 2, closeParen - (closeBracket + 2));
                    addHit(static_cast<int>(i), static_cast<int>(closeParen + 1), urlPart);
                    i = closeParen + 1;
                    continue;
                }
            }
        }
        i++;
    }

    // 2. Scan space/boundary delimited tokens
    i = 0;
    while (i < n) {
        while (i < n && isWhitespaceOrDelimiter(line[i])) i++;
        if (i >= n) break;

        size_t start = i;
        while (i < n && !isWhitespaceOrDelimiter(line[i])) i++;
        size_t end = i;

        std::string_view token = line.substr(start, end - start);
        addHit(static_cast<int>(start), static_cast<int>(end), token);
    }

    std::sort(hits.begin(), hits.end(), [](const UrlSpan& a, const UrlSpan& b) {
        return a.start < b.start;
    });

    return hits;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_termux_lite_NativeBridge_nativeFindUrlAt(
    JNIEnv* env,
    jclass /* clazz */,
    jstring line_jstr,
    jint col
) {
    if (!line_jstr) return nullptr;

    const char* native_chars = env->GetStringUTFChars(line_jstr, nullptr);
    if (!native_chars) return nullptr;

    jsize len = env->GetStringUTFLength(line_jstr);
    if (len == 0) {
        env->ReleaseStringUTFChars(line_jstr, native_chars);
        return nullptr;
    }

    std::string_view line(native_chars, len);
    auto hits = scanLine(line);

    std::string result;
    if (!hits.empty()) {
        int target = std::clamp(static_cast<int>(col), 0, static_cast<int>(len - 1));

        // Exact hit
        for (const auto& hit : hits) {
            if (target >= hit.start && target < hit.end) {
                result = hit.url;
                break;
            }
        }

        // Nearby hit (within 3 characters)
        if (result.empty()) {
            auto nearest = std::min_element(hits.begin(), hits.end(), [&](const UrlSpan& a, const UrlSpan& b) {
                return distanceToHit(target, a) < distanceToHit(target, b);
            });
            if (nearest != hits.end() && distanceToHit(target, *nearest) <= 3) {
                result = nearest->url;
            }
        }

        // Single hit on line
        if (result.empty() && hits.size() == 1) {
            result = hits[0].url;
        }
    }

    env->ReleaseStringUTFChars(line_jstr, native_chars);

    if (result.empty()) return nullptr;
    return env->NewStringUTF(result.c_str());
}
