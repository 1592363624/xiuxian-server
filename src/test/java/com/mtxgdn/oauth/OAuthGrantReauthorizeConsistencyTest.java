package com.mtxgdn.oauth;

import com.mtxgdn.TestBase;
import com.mtxgdn.db.DatabaseManager;
import com.mtxgdn.db.OAuthGrantsDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 重复授权时，内存与数据库对「授权时间」的看法必须一致。
 *
 * <h3>这里曾经有不一致</h3>
 * <p>{@code authorize} 每次都 new 一个 {@link OAuthGrantStore.Grant}，
 * 它的 {@code createdAt} 是"现在"。而落库用的 upsert <b>只更新</b>
 * scopes / last_used_at / expires_at，<b>不更新</b> created_at。</p>
 *
 * <p>于是同一个授权在两边有了不同的创建时间：</p>
 * <ul>
 *   <li>内存：「已授权应用」页显示"刚刚授权"（正确，这确实是重新授权）</li>
 *   <li>数据库：保留首次授权时间</li>
 * </ul>
 *
 * <p>重启后载入的是数据库那份，页面上的时间会"跳回"很久以前 ——
 * 玩家会困惑"我刚刚重新授权的，怎么显示三个月前"。
 * 更实际的影响是 <b>{@code expires_at} 也按新的 createdAt 算</b>，
 * 清理逻辑的保留期限随之漂移。</p>
 *
 * <h3>选定语义：重新授权 = 新的一次授权</h3>
 * <p>玩家重新点"同意"，意味着他重新看了一眼权限并再次同意。
 * 把它当作新的授权起点更符合直觉，也让"最近授权时间"这个展示有意义。
 * 所以是<b>数据库向内存看齐</b>：upsert 也更新 created_at，
 * 而不是让内存去迁就库里的旧时间。</p>
 */
class OAuthGrantReauthorizeConsistencyTest extends TestBase {

    private static final long USER = 8801L;
    private static final String CLIENT = "consistency_app";
    private static final String GRANT_ID = USER + ":" + CLIENT;

    private final OAuthGrantsDAO dao = new OAuthGrantsDAO();

    @BeforeEach
    void setUp() throws SQLException {
        cleanTable();
        OAuthGrantStore.clear();
    }

    @AfterEach
    void tearDown() throws SQLException {
        OAuthGrantStore.clear();
        cleanTable();
    }

    private void cleanTable() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM oauth_grants")) {
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("重复授权后，内存与库里的 created_at 一致")
    void reauthorizeKeepsCreatedAtConsistent() throws Exception {
        OAuthGrantStore.authorize(USER, "张三", CLIENT, Set.of("player:read"));

        // 等 5ms 让时间戳必然不同
        Thread.sleep(5);
        OAuthGrantStore.authorize(USER, "张三", CLIENT, Set.of("inventory:read"));

        long inMemory = OAuthGrantStore.grantsOf(USER).get(0).createdAt();
        long inDatabase = dao.find(GRANT_ID).createdAt();

        assertEquals(inMemory, inDatabase,
                "两边时间不一致会导致重启后页面显示的时间『跳回去』，"
                        + "玩家会以为自己没有重新授权过");
    }

    @Test
    @DisplayName("重启载入后显示的授权时间与重启前一致")
    void reloadedCreatedAtMatchesWhatUserSaw() throws Exception {
        OAuthGrantStore.authorize(USER, "张三", CLIENT, Set.of("player:read"));
        Thread.sleep(5);
        OAuthGrantStore.authorize(USER, "张三", CLIENT, Set.of("inventory:read"));

        long beforeRestart = OAuthGrantStore.grantsOf(USER).get(0).createdAt();

        OAuthGrantStore.clear();
        OAuthGrantStore.loadFromDatabase();

        assertEquals(beforeRestart, OAuthGrantStore.grantsOf(USER).get(0).createdAt(),
                "重启后页面上显示的授权时间不能变 —— 这是玩家判断"
                        + "『这是不是我授权的』的主要依据");
    }
}
