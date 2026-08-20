#[derive(Debug, Clone, PartialEq, Eq)]
pub struct UrlSpan {
    pub start: usize,
    pub end: usize,
    pub url: String,
}

#[inline]
fn is_whitespace_or_delimiter(c: char) -> bool {
    c.is_whitespace() || matches!(c, '<' | '>' | '"' | '\'' | ']' | '}' | '^' | '`')
}

#[inline]
fn is_trailing_punct(c: char) -> bool {
    matches!(c, '.' | ',' | ';' | ':' | '!' | '?' | ')' | ']' | '}' | '>' | '"' | '\'')
}

#[inline]
fn is_leading_punct(c: char) -> bool {
    matches!(c, '(' | '[' | '{' | '"' | '\'' | '<')
}

pub fn normalize_url(raw: &str) -> Option<String> {
    let mut trimmed = raw;

    while let Some(first) = trimmed.chars().next() {
        if is_leading_punct(first) {
            trimmed = &trimmed[first.len_utf8()..];
        } else {
            break;
        }
    }

    while let Some(last) = trimmed.chars().last() {
        if is_trailing_punct(last) {
            trimmed = &trimmed[..trimmed.len() - last.len_utf8()];
        } else {
            break;
        }
    }

    if trimmed.len() < 4 {
        return None;
    }

    let lower = trimmed.to_ascii_lowercase();

    if lower.starts_with("https://")
        || lower.starts_with("http://")
        || lower.starts_with("ftp://")
        || lower.starts_with("ftps://")
        || lower.starts_with("file://")
        || lower.starts_with("mailto:")
    {
        if (lower.starts_with("http://") || lower.starts_with("https://")) && trimmed.len() < 10 {
            return None;
        }
        return Some(trimmed.to_string());
    }

    if lower.starts_with("www.") {
        return Some(format!("https://{trimmed}"));
    }

    if lower.starts_with("github.com/")
        || lower.starts_with("gitlab.com/")
        || lower.starts_with("bitbucket.com/")
        || lower.starts_with("x.com/")
        || lower.starts_with("twitter.com/")
    {
        return Some(format!("https://{trimmed}"));
    }

    if trimmed.contains("://") {
        return Some(trimmed.to_string());
    }

    None
}

fn distance_to_hit(target: usize, hit: &UrlSpan) -> usize {
    if target < hit.start {
        hit.start - target
    } else if target >= hit.end {
        target - (hit.end - 1)
    } else {
        0
    }
}

pub fn scan_line(line: &str) -> Vec<UrlSpan> {
    let mut hits: Vec<UrlSpan> = Vec::new();
    let n = line.len();
    let bytes = line.as_bytes();

    // 1. Markdown link check: [label](https://url)
    let mut i = 0;
    while i < n {
        if bytes[i] == b'[' {
            if let Some(close_bracket_rel) = line[i + 1..].find(']') {
                let close_bracket = i + 1 + close_bracket_rel;
                if close_bracket + 1 < n && bytes[close_bracket + 1] == b'(' {
                    if let Some(close_paren_rel) = line[close_bracket + 2..].find(')') {
                        let close_paren = close_bracket + 2 + close_paren_rel;
                        let url_part = &line[close_bracket + 2..close_paren];
                        if let Some(norm) = normalize_url(url_part) {
                            let start = i;
                            let end = close_paren + 1;
                            if !hits.iter().any(|h| start < h.end && end > h.start && h.url == norm) {
                                hits.push(UrlSpan {
                                    start,
                                    end,
                                    url: norm,
                                });
                            }
                        }
                        i = close_paren + 1;
                        continue;
                    }
                }
            }
        }
        i += 1;
    }

    // 2. Scan space/boundary delimited tokens
    i = 0;
    while i < n {
        while i < n && is_whitespace_or_delimiter(line[i..].chars().next().unwrap()) {
            i += line[i..].chars().next().unwrap().len_utf8();
        }
        if i >= n {
            break;
        }

        let start = i;
        while i < n && !is_whitespace_or_delimiter(line[i..].chars().next().unwrap()) {
            i += line[i..].chars().next().unwrap().len_utf8();
        }
        let end = i;

        let token = &line[start..end];
        if let Some(norm) = normalize_url(token) {
            if !hits.iter().any(|h| start < h.end && end > h.start && h.url == norm) {
                hits.push(UrlSpan {
                    start,
                    end,
                    url: norm,
                });
            }
        }
    }

    hits.sort_by_key(|h| h.start);
    hits
}

pub fn find_url_at(line: &str, col: usize) -> Option<String> {
    if line.is_empty() {
        return None;
    }

    let hits = scan_line(line);
    if hits.is_empty() {
        return None;
    }

    let target = col.min(line.len().saturating_sub(1));

    // Exact hit
    for hit in &hits {
        if target >= hit.start && target < hit.end {
            return Some(hit.url.clone());
        }
    }

    // Nearby hit (within 3 characters)
    if let Some(nearest) = hits.iter().min_by_key(|h| distance_to_hit(target, h)) {
        if distance_to_hit(target, nearest) <= 3 {
            return Some(nearest.url.clone());
        }
    }

    // Single hit on line fallback
    if hits.len() == 1 {
        return Some(hits[0].url.clone());
    }

    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_normalize_url() {
        assert_eq!(
            normalize_url("https://github.com/termux"),
            Some("https://github.com/termux".to_string())
        );
        assert_eq!(
            normalize_url("(https://example.com)"),
            Some("https://example.com".to_string())
        );
        assert_eq!(
            normalize_url("www.google.com"),
            Some("https://www.google.com".to_string())
        );
        assert_eq!(
            normalize_url("github.com/termux/termux-app"),
            Some("https://github.com/termux/termux-app".to_string())
        );
        assert_eq!(
            normalize_url("custom://resource/item"),
            Some("custom://resource/item".to_string())
        );
        assert_eq!(normalize_url("http://"), None);
        assert_eq!(normalize_url("abc"), None);
    }

    #[test]
    fn test_scan_markdown_link() {
        let line = "Check out [Termux Repo](https://github.com/termux) for source.";
        let hits = scan_line(line);
        assert_eq!(hits.len(), 1);
        assert_eq!(hits[0].url, "https://github.com/termux");
    }

    #[test]
    fn test_find_url_at_exact_and_nearby() {
        let line = "Visit https://termux.dev today!";
        // exact inside URL
        assert_eq!(find_url_at(line, 10), Some("https://termux.dev".to_string()));
        // nearby near URL start
        assert_eq!(find_url_at(line, 3), Some("https://termux.dev".to_string()));
        // single hit fallback when far away
        assert_eq!(find_url_at(line, 0), Some("https://termux.dev".to_string()));
    }

    #[test]
    fn test_multiple_urls_on_line() {
        let line = "https://one.com and https://two.com";
        assert_eq!(find_url_at(line, 2), Some("https://one.com".to_string()));
        assert_eq!(find_url_at(line, 25), Some("https://two.com".to_string()));
    }
}
