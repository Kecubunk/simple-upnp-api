package eu.mihosoft.upnp.sonos;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.model.message.header.UDAServiceTypeHeader;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensin.sonos.SonosConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.tensin.sonos.model.ZoneGroupState;

/**
 * Based on code from
 * <a href="https://github.com/bertjan/sonos-java-api">
 * https://github.com/bertjan/sonos-java-api</a>
 */
public class ZonePlayers {

    private static final Logger LOG
            = LoggerFactory.getLogger(ZonePlayers.class);

    private final List<ZonePlayer> zones
            = Collections.synchronizedList(new ArrayList<>());

    private List<ZoneGroup> zoneGroups
            = Collections.synchronizedList(new ArrayList<>());

    public static ZonePlayers discover() {
        ZonePlayers zonePlayers = new ZonePlayers();

        final UpnpService lookupService = new UpnpServiceImpl();
        final UpnpService upnpService = new UpnpServiceImpl(
                new DefaultRegistryListener() {
                    @Override
                    public void remoteDeviceAdded(
                            Registry registry, RemoteDevice device) {
                                if (zonePlayers.isSonos(device)) {
                                    ZonePlayer zP = new ZonePlayer(
                                            device, lookupService);
                                    zonePlayers.add(zP);

                                    zonePlayers.updateZoneGroups(zP, 5000);
                                    System.out.println("DISCOVERED: " + zP);
                                }
                            }
                });

        upnpService.getControlPoint().search(
                new UDAServiceTypeHeader(new UDAServiceType(
                                SonosConstants.AV_TRANSPORT)), 120);

        return zonePlayers;
    }

    private void updateZoneGroups(ZonePlayer zP, long timeout) {

        new Thread(() -> {

            zoneGroups.clear();

            long start = System.currentTimeMillis();

            while (System.currentTimeMillis() - start < timeout) {

                try {
                    LOG.debug("Adding groups");
                    zoneGroups.addAll(zP.getZoneGroups(this));
                    break;
                } catch (Exception ex) {
                    LOG.debug("zonegroups could not be added");
                }

                Utils.sleep(100);
            }
        }).start();

    }

    public List<ZoneGroup> getZoneGroups(int timeout) {

        getPlayers(timeout);

        // fix duplicates
        zoneGroups = zoneGroups.stream().filter(distinctByKey(g -> g.getId())).
                collect(Collectors.toList());

        return zoneGroups;
    }

    public List<ZoneGroup> getDiscoveredZoneGroups() {

        // fix duplicates
        zoneGroups = zoneGroups.stream().filter(distinctByKey(g -> g.getId())).
                collect(Collectors.toList());

        return zoneGroups;
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

    public List<ZonePlayer> getPlayers(int timeout) {
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < timeout) {

            Utils.sleep(100);
        }

        return zones;
    }

    public List<ZonePlayer> getDiscoveredPlayers() {
        return zones;
    }

    public ZonePlayer get(String zoneName, int timeout) {
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < timeout) {
            for (ZonePlayer zone : zones) {
                if (zone.getZoneName().equalsIgnoreCase(zoneName)) {
                    return zone;
                }
            }

            Utils.sleep(100);
        }

        // Not found, timeout expired.
        LOG.info("returning null");
        return null;
    }

    private void add(ZonePlayer zone) {
        zones.add(zone);
    }

    private boolean isSonos(RemoteDevice device) {
        return "urn:schemas-upnp-org:device:ZonePlayer:1".
                equals(device.getType().toString());
    }

}
