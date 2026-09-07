import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Pagination } from "../components/Pagination.jsx";
import { useAuth } from "../context/AuthContext.jsx";
import { callPublicApi } from "../lib/http.js";
import { formatRelativeTime, getErrorMessage } from "../lib/format.js";

export function CommunityListPage() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [searchType, setSearchType] = useState("title");
  const [keyword, setKeyword] = useState("");
  const [pageData, setPageData] = useState({
    content: [],
    page: 0,
    totalPages: 0,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadPosts = async (page, currentSearchType = searchType, currentKeyword = keyword) => {
    setLoading(true);
    setError("");
    try {
      const params = new URLSearchParams({
        page: String(page),
        size: "10",
        sort: "createdAt,desc",
      });

      if (currentKeyword.trim()) {
        params.set("searchType", currentSearchType);
        params.set("keyword", currentKeyword.trim());
      }

      const result = await callPublicApi(`/api/communities?${params.toString()}`);
      setPageData(result.data);
    } catch (e) {
      setError(getErrorMessage(e, "커뮤니티 목록을 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPosts(0);
  }, []);

  const onSearch = () => {
    loadPosts(0, searchType, keyword);
  };

  return (
    <section>
      <div className="page-header">
        <h1>
          <span className="material-icons">forum</span>
          커뮤니티
        </h1>
        {isAuthenticated && (
          <div>
            <Link className="btn btn-primary" to="/community/write">
              <span className="material-icons">edit</span>
              글쓰기
            </Link>
          </div>
        )}
      </div>

      <div className="search-filter-section">
        <select className="filter-select" value={searchType} onChange={(e) => setSearchType(e.target.value)}>
          <option value="title">제목</option>
          <option value="nickname">작성자</option>
        </select>
        <div className="search-input-wrapper">
          <span className="material-icons">search</span>
          <input
            placeholder="검색어를 입력하세요..."
            type="text"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") onSearch();
            }}
          />
        </div>
        <button className="btn btn-primary" type="button" onClick={onSearch}>
          <span className="material-icons">search</span>
          검색
        </button>
      </div>

      {loading && (
        <div className="empty-state" style={{ paddingTop: 40, paddingBottom: 40 }}>
          <div style={{ fontSize: 14 }}>로딩 중...</div>
        </div>
      )}
      {!loading && error && (
        <div className="empty-state">
          <span className="material-icons">error</span>
          <h3>목록을 불러오지 못했습니다</h3>
          <p className="error-text">{error}</p>
        </div>
      )}
      {!loading && !error && pageData.content?.length === 0 && (
        <div className="empty-state">
          <span className="material-icons">article</span>
          <h3>게시글이 없습니다</h3>
          <p>첫 번째 게시글을 작성해보세요!</p>
        </div>
      )}

      {!loading && !error && pageData.content?.length > 0 && (
        <>
          <div className="post-list">
            {pageData.content.map((post) => (
              <div key={post.id} className="post-item" onClick={() => navigate(`/community/detail?id=${post.id}`)}>
                <div className="post-header">
                  <div>
                    <div className="post-title">{post.title}</div>
                    <div className="post-meta">
                      <span className="post-meta-item">
                        <span className="material-icons">person</span>
                        <span>{post.nickname || post.username}</span>
                      </span>
                      <span className="post-meta-item">
                        <span className="material-icons">schedule</span>
                        <span>{formatRelativeTime(post.createdAt)}</span>
                      </span>
                    </div>
                  </div>
                </div>
                <div className="post-stats">
                  <span className="post-stat">
                    <span className="material-icons">visibility</span>
                    <span>{post.viewCount ?? 0}</span>
                  </span>
                  <span className="post-stat">
                    <span className="material-icons">comment</span>
                    <span>{post.commentCount ?? 0}</span>
                  </span>
                </div>
              </div>
            ))}
          </div>

          <Pagination
            page={pageData.page ?? 0}
            totalPages={pageData.totalPages ?? 0}
            onChange={(nextPage) => loadPosts(nextPage)}
          />
        </>
      )}
    </section>
  );
}
