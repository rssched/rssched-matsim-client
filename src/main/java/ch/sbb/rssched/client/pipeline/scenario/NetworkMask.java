package ch.sbb.rssched.client.pipeline.scenario;

import ch.sbb.rssched.client.pipeline.core.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt.transitSchedule.api.TransitLine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Masks the network based on transit line IDs of interest.
 *
 * @author munterfi
 */
@RequiredArgsConstructor
@Log4j2
class NetworkMask implements Filter<ScenarioPipe> {

    private final Strategy strategy;

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
        strategy.maskLinks(pipe);
        maskNodes(pipe.scenario);
        log.info("Done (remaining nodes: {}, links: {})", pipe.scenario.getNetwork().getNodes().size(),
                pipe.scenario.getNetwork().getLinks().size());
    }

    interface Strategy {
        void maskLinks(ScenarioPipe pipe);
    }

    @RequiredArgsConstructor
    static class LinksWithAllowedMode implements Strategy {

        private final String allowedMode;

        @Override
        public void maskLinks(ScenarioPipe pipe) {
            var network = pipe.scenario.getNetwork();

            Set<Id<Link>> linkIdsToKeep = network.getLinks()
                    .values()
                    .stream()
                    .filter(link -> link.getAllowedModes().contains(allowedMode)) // Check if link's mode is allowed
                    .map(Link::getId)
                    .collect(Collectors.toSet());

            network.getLinks()
                    .keySet()
                    .stream()
                    .filter(linkId -> !linkIdsToKeep.contains(linkId))
                    .forEach(network::removeLink);
        }
    }

    static class LinksWithPassingTransitLines implements Strategy {

        @Override
        public void maskLinks(ScenarioPipe pipe) {
            var transitSchedule = pipe.scenario.getTransitSchedule();
            var network = pipe.scenario.getNetwork();
            var transitLineIds = pipe.selection.getLineIds();

            Set<Id<Link>> linkIdsToKeep = transitLineIds.stream()
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

            List<Id<Link>> linkIdsToRemove = network.getLinks()
                    .keySet()
                    .stream()
                    .filter(linkId -> !linkIdsToKeep.contains(linkId))
                    .toList();

            linkIdsToRemove.forEach(network::removeLink);
        }
    }

}
