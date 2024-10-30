package ch.sbb.rssched.client.pipeline.scenario;

import ch.sbb.rssched.client.pipeline.core.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Masks the network based on transit line IDs of interest and the allowed modes for pt
 *
 * @author munterfi
 */
@RequiredArgsConstructor
@Log4j2
class NetworkMask implements Filter<ScenarioPipe> {

    public static final String DEAD_HEAD_TRIP_MODE = "deadHeadTrip";
    public static final String SERVICE_TRIP_MODE = "serviceTrip";

    private final Set<String> allowedModes;

    private static void maskNodes(Scenario scenario) {
        // collect IDs of remaining links
        var network = scenario.getNetwork();
        List<Id<Link>> remainingLinkIds = network.getLinks().keySet().stream().toList();
        var nodeIdsToKeep = remainingLinkIds.stream()
                .flatMap(linkId -> Stream.of(network.getLinks().get(linkId).getFromNode().getId(),
                        network.getLinks().get(linkId).getToNode().getId()))
                .distinct()
                .toList();

        network.getNodes()
                .keySet()
                .stream()
                .filter(nodeId -> !nodeIdsToKeep.contains(nodeId))
                .forEach(network::removeNode);
    }

    @Override
    public void apply(ScenarioPipe pipe) {
        log.info("Masking network (nodes: {}, links: {})", pipe.scenario.getNetwork().getNodes().size(),
                pipe.scenario.getNetwork().getLinks().size());
        maskLinks(pipe.scenario, pipe.selection.getLineIds());
        maskNodes(pipe.scenario);
        log.info("Done (remaining nodes: {}, links: {})", pipe.scenario.getNetwork().getNodes().size(),
                pipe.scenario.getNetwork().getLinks().size());
    }


    public void maskLinks(Scenario scenario, Set<Id<TransitLine>> transitLineIds) {
        var transitSchedule = scenario.getTransitSchedule();
        var network = scenario.getNetwork();

        // get link ids
        Set<Id<Link>> transitLineLinkIds = getTransitLineLinkIds(transitLineIds, transitSchedule);
        Set<Id<Link>> allowedModeLinkIds = getLinksWithAllowedMode(network);
        ensureAllTransitLinksAreAllowed(network, allowedModeLinkIds, transitLineLinkIds);

        // remove not needed links from network
        retainLinks(network, allowedModeLinkIds);

        // write rssched modes
        clearAllowedModes(network);
        addAllowedMode(network, allowedModeLinkIds, DEAD_HEAD_TRIP_MODE);
        addAllowedMode(network, transitLineLinkIds, SERVICE_TRIP_MODE);
    }

    private void ensureAllTransitLinksAreAllowed(Network network, Set<Id<Link>> allowedLinks, Set<Id<Link>> transitLinks) {
        if (!allowedLinks.containsAll(transitLinks)) {
            var missingLinks = transitLinks.stream().filter(id -> !allowedLinks.contains(id)).map(linkId -> {
                var link = network.getLinks().get(linkId);
                return String.format("%s (%s)", linkId, link.getAllowedModes());
            }).toList();
            throw new IllegalStateException(
                    String.format("The following transit line links are missing the allowed modes (%s): %s",
                            allowedModes, missingLinks));
        }
    }

    private static void clearAllowedModes(Network network) {
        network.getLinks().values().forEach(link -> link.setAllowedModes(new HashSet<>()));
    }

    private void addAllowedMode(Network network, Set<Id<Link>> linksIds, String allowedMode) {
        network.getLinks().values().stream().filter(link -> linksIds.contains(link.getId())).forEach(link -> {
            var linkAllowedModes = new HashSet<>(link.getAllowedModes());
            linkAllowedModes.add(allowedMode);
            link.setAllowedModes(linkAllowedModes);
        });
    }

    private static void retainLinks(Network network, Set<Id<Link>> allowedModeLinkIds) {
        network.getLinks()
                .keySet()
                .stream()
                .filter(linkId -> !allowedModeLinkIds.contains(linkId))
                .forEach(network::removeLink);
    }

    private static Set<Id<Link>> getTransitLineLinkIds(Set<Id<TransitLine>> transitLineIds, TransitSchedule transitSchedule) {
        return transitLineIds.stream()
                .flatMap(transitLineId -> transitSchedule.getTransitLines()
                        .get(transitLineId)
                        .getRoutes()
                        .values()
                        .stream()
                        .flatMap(transitRoute -> {
                            Set<Id<Link>> linkIds = new HashSet<>(transitRoute.getRoute().getLinkIds());
                            linkIds.add(transitRoute.getStops().get(0).getStopFacility().getLinkId());
                            linkIds.add(transitRoute.getStops()
                                    .get(transitRoute.getStops().size() - 1)
                                    .getStopFacility()
                                    .getLinkId());
                            return linkIds.stream();
                        }))
                .collect(Collectors.toSet());
    }

    private Set<Id<Link>> getLinksWithAllowedMode(Network network) {
        return network.getLinks()
                .values()
                .stream()
                .filter(link -> link.getAllowedModes()
                        .stream()
                        .anyMatch(allowedModes::contains)) // check if link's mode is in allowedModes set
                .map(Identifiable::getId)
                .collect(Collectors.toSet());
    }
}
