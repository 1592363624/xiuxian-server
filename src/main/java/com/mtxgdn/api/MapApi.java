package com.mtxgdn.api;

import com.mtxgdn.game.entity.MapLocation;
import com.mtxgdn.game.map.GatherResult;
import com.mtxgdn.game.map.PlayerSurroundings;
import com.mtxgdn.game.map.TravelResult;
import com.mtxgdn.game.map.WildernessChunk;
import com.mtxgdn.game.map.WildernessStats;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MapService 的对外接口。
 *
 * <p>由脚本从实现类机械抽取（剔除构造函数、静态方法、对 JDBC 与其它服务类型的引用）。
 * 目的是让插件层不再依赖 com.mtxgdn.game.service 的具体类。</p>
 */
public interface MapApi {

    void ensureInitialized();

    void reload();

    void invalidateCache();

    int getTravelTimeSeconds(long fromId, long toId);

    MapLocation getLocation(long id);

    List<MapLocation> getNeighbors(long locationId);

    MapLocation getLocationByName(String name);

    MapLocation getLocationByToken(String token);

    String getLocationName(long id);

    double getCultivationBonus(long locationId);

    double getExplorationBonus(long locationId);

    Map<String, Double> getEventBias(long locationId);

    List<MapLocation> getAllLocations();

    List<MapLocation> getLocationsByRegion(String region);

    String getRealmName(int realm);

    long getPlayerLocationId(long playerId);

    boolean isLocationKnown(long id);

    long getPlayerLastTravelTime(long playerId);

    TravelResult travel(long playerId, long targetLocationId);

    TravelResult teleportToLocation(long playerId, long targetLocationId);

    PlayerSurroundings getPlayerSurroundings(long playerId);

    Map<Long, String> getPlayerLocationNames(List<Long> playerIds);

    int getDangerLevel(long locationId);

    String getDangerName(int dangerLevel);

    boolean isSafeZone(long locationId);

    List<MapLocation> getKnownWorldLocations();

    boolean isWildernessEnabled();

    List<MapLocation> getChunkNodes(int chunkX, int chunkY);

    int[] getChunkOf(long locationId);

    WildernessChunk getChunk(int chunkX, int chunkY);

    boolean isChunkPinned(int chunkX, int chunkY);

    String recordFirstVisit(long playerId, long locationId);

    boolean isExplored(long playerId, long locationId);

    Set<Long> getExploredLocationIds(long playerId);

    long countExploredKnownWorld(long playerId);

    long getTeleportCost(long fromLocationId, long targetLocationId);

    TravelResult teleportByUnlock(long playerId, long targetLocationId);

    Map<String, Integer> getWildernessResource(long locationId);

    void maintainWildernessIfNeeded();

    int restockWilderness();

    WildernessStats getWildernessStats();

    GatherResult gather(long playerId);

}
