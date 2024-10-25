package ch.sbb.rssched.client.config.selection;

import java.util.Set;

public record VehicleCategory(String group, Set<String> vehicleTypes) {
}
