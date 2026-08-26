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

fn unmatched_open_paren(s: &str) -> bool {
    let opens = s.bytes().filter(|&b| b == b'(').count();
    let closes = s.bytes().filter(|&b| b == b')').count();
    opens > closes
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
        if !is_trailing_punct(last) {
            break;
        }
        if last == ')' && unmatched_open_paren(&trimmed[..trimmed.len() - last.len_utf8()]) {
            break;
        }
        trimmed = &trimmed[..trimmed.len() - last.len_utf8()];
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
        || lower.starts_with("magnet:")
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

fn push_unique(hits: &mut Vec<UrlSpan>, start: usize, end: usize, url: String) {
    if hits
        .iter()
        .any(|h| start < h.end && end > h.start && h.url == url)
    {
        return;
    }
    hits.push(UrlSpan { start, end, url });
}

fn scan_markdown(line: &str, hits: &mut Vec<UrlSpan>) {
    let bytes = line.as_bytes();
    let n = line.len();
    let mut i = 0;
    while i < n {
        if bytes[i] != b'[' {
            i += 1;
            continue;
        }
        let Some(close_bracket_rel) = line[i + 1..].find(']') else {
            break;
        };
        let close_bracket = i + 1 + close_bracket_rel;
        if close_bracket + 1 >= n || bytes[close_bracket + 1] != b'(' {
            i += 1;
            continue;
        }
        let url_start = close_bracket + 2;
        let mut depth: i32 = 1;
        let mut k = url_start;
        while k < n && depth > 0 {
            match bytes[k] {
                b'(' => depth += 1,
                b')' => depth -= 1,
                _ => {}
            }
            if depth == 0 {
                break;
            }
            k += 1;
        }
        let url_end = if depth == 0 { k } else { n };
        let url_part = line[url_start..url_end].trim().split_whitespace().next().unwrap_or("");
        if let Some(norm) = normalize_url(url_part) {
            let end = if depth == 0 {
                k + 1
            } else {
                (url_start + url_part.len()).min(url_end)
            };
            push_unique(hits, i, end, norm);
            i = end.max(i + 1);
        } else {
            i = url_end.max(i + 1);
        }
    }
}

pub fn scan_line(line: &str) -> Vec<UrlSpan> {
    let mut hits: Vec<UrlSpan> = Vec::new();
    let n = line.len();
    let bytes = line.as_bytes();

    scan_markdown(line, &mut hits);

    // Scan space/boundary delimited tokens
    let mut i = 0;
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
            // Parenthesized URL: include a short adjacent label so tapping
            // `The Tribune (https://…)` still opens the href.
            let mut hit_start = start;
            let mut s = start;
            if s < n && bytes[s] != b'(' && s > 0 && bytes[s - 1] == b'(' {
                s -= 1;
            }
            if s < n && bytes[s] == b'(' && s > 0 {
                let before = &line[..s];
                if let Some(last_non_space) = before.rfind(|c: char| !c.is_whitespace()) {
                    if before.as_bytes().get(last_non_space) != Some(&b']') {
                        let label_start = before[..last_non_space]
                            .rfind(|c: char| "\t()[]{}<>\"'".contains(c))
                            .map(|p| p + 1)
                            .unwrap_or(0);
                        let label = line[label_start..=last_non_space].trim();
                        if (2..=80).contains(&label.len()) {
                            hit_start = label_start;
                        }
                    }
                }
            }
            push_unique(&mut hits, hit_start, end, norm);
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

    for hit in &hits {
        if target >= hit.start && target < hit.end {
            return Some(hit.url.clone());
        }
    }

    if let Some(nearest) = hits.iter().min_by_key(|h| distance_to_hit(target, h)) {
        if distance_to_hit(target, nearest) <= 3 {
            return Some(nearest.url.clone());
        }
    }

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
        assert_eq!(
            normalize_url("https://en.wikipedia.org/wiki/Foo_(bar)"),
            Some("https://en.wikipedia.org/wiki/Foo_(bar)".to_string())
        );
        assert_eq!(
            normalize_url("x.com/foo/status/2090113724669694247"),
            Some("https://x.com/foo/status/2090113724669694247".to_string())
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
        assert_eq!(
            find_url_at(line, line.find("Termux").unwrap()),
            Some("https://github.com/termux".to_string())
        );
    }

    #[test]
    fn test_markdown_nested_parens_and_label() {
        let line = "([The Tribune](https://www.tribuneindia.com/news/foo))";
        assert_eq!(
            find_url_at(line, line.find("Tribune").unwrap()),
            Some("https://www.tribuneindia.com/news/foo".to_string())
        );
        assert_eq!(
            find_url_at(line, line.find("https").unwrap()),
            Some("https://www.tribuneindia.com/news/foo".to_string())
        );
    }

    #[test]
    fn test_unclosed_markdown() {
        let line = "See [the docs](https://example.com/docs";
        assert_eq!(
            find_url_at(line, line.find("docs").unwrap()),
            Some("https://example.com/docs".to_string())
        );
    }

    #[test]
    fn test_x_status_url() {
        let line = "https://x.com/KesariPunjab/status/2082454538771169297";
        assert_eq!(find_url_at(line, 0), Some(line.to_string()));
        assert_eq!(
            find_url_at(line, line.len() - 2),
            Some(line.to_string())
        );
    }

    #[test]
    fn test_find_url_at_exact_and_nearby() {
        let line = "Visit https://termux.dev today!";
        assert_eq!(find_url_at(line, 10), Some("https://termux.dev".to_string()));
        assert_eq!(find_url_at(line, 3), Some("https://termux.dev".to_string()));
        assert_eq!(find_url_at(line, 0), Some("https://termux.dev".to_string()));
    }

    #[test]
    fn test_multiple_urls_on_line() {
        let line = "https://one.com and https://two.com";
        assert_eq!(find_url_at(line, 2), Some("https://one.com".to_string()));
        assert_eq!(find_url_at(line, 25), Some("https://two.com".to_string()));
    }

    #[test]
    fn test_paren_label_opens_href() {
        let line = "The Tribune (https://example.com/x)";
        assert_eq!(
            find_url_at(line, 0),
            Some("https://example.com/x".to_string())
        );
    }
}
