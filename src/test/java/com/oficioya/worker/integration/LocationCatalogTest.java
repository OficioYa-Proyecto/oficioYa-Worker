package com.oficioya.worker.integration;

import com.oficioya.worker.exception.ExternalServiceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationCatalogTest {

    private static final Executor SYNC = Runnable::run;

    @Mock
    private LocationCatalogClient client;

    private LocationCatalog catalog(Duration ttl) {
        return new LocationCatalog(client, ttl, SYNC);
    }

    private void stubCatalog() {
        when(client.findLocalities()).thenReturn(List.of(
                new LocationCatalogClient.Locality(1L, "Usaquén"),
                new LocationCatalogClient.Locality(2L, "Chapinero"),
                new LocationCatalogClient.Locality(3L, "Sumapaz")));
        when(client.findZones()).thenReturn(List.of(
                new LocationCatalogClient.Zone(10L, "Cedritos", "USAQUEN"),
                new LocationCatalogClient.Zone(11L, "Toberín", "Usaquén"),
                new LocationCatalogClient.Zone(20L, "Chicó", " chapinero "),
                new LocationCatalogClient.Zone(99L, "Sin localidad", "")));
    }

    private ExternalServiceUnavailableException usersDown() {
        return new ExternalServiceUnavailableException("Users service unavailable (zones)", new RuntimeException("down"));
    }

    @Test
    @DisplayName("Empareja barrios y localidades sin importar tildes, mayúsculas ni espacios")
    void mapsZonesToLocalities() {
        stubCatalog();
        LocationCatalog catalog = catalog(Duration.ofMinutes(10));

        assertEquals(Optional.of(1L), catalog.localityOfZone(10L));
        assertEquals(Optional.of(2L), catalog.localityOfZone(20L));
        assertEquals(Set.of(10L, 11L), catalog.zonesOfLocality(1L));
        assertEquals(Optional.empty(), catalog.localityOfZone(99L));
        assertEquals(Set.of(), catalog.zonesOfLocality(3L));
    }

    @Test
    @DisplayName("Mientras no vence, consulta a Users una sola vez")
    void loadsOnlyOnceWhileFresh() {
        stubCatalog();
        LocationCatalog catalog = catalog(Duration.ofMinutes(10));

        catalog.localityOfZone(10L);
        catalog.zonesOfLocality(1L);
        catalog.localityOfZone(20L);

        verify(client, times(1)).findZones();
        verify(client, times(1)).findLocalities();
    }

    @Test
    @DisplayName("Valida que la localidad exista, aunque no tenga barrios")
    void requireLocality() {
        stubCatalog();
        LocationCatalog catalog = catalog(Duration.ofMinutes(10));

        catalog.requireLocality(3L);
        assertThrows(IllegalArgumentException.class, () -> catalog.requireLocality(77L));
    }

    @Test
    @DisplayName("Valida que los barrios existan")
    void requireZones() {
        stubCatalog();
        LocationCatalog catalog = catalog(Duration.ofMinutes(10));

        catalog.requireZones(Set.of(10L, 99L));
        assertThrows(IllegalArgumentException.class, () -> catalog.requireZones(Set.of(10L, 500L)));
    }

    @Test
    @DisplayName("Sin barrios que validar no consulta a Users")
    void requireZonesWithNoIdsDoesNotCallUsers() {
        catalog(Duration.ofMinutes(10)).requireZones(Set.of());

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("Si el refresco falla, sigue usando los últimos datos")
    void keepsPreviousDataWhenRefreshFails() {
        stubCatalog();
        LocationCatalog catalog = catalog(Duration.ZERO);
        catalog.localityOfZone(10L);

        when(client.findZones()).thenThrow(usersDown());

        assertEquals(Optional.of(1L), catalog.localityOfZone(10L));
        assertEquals(Set.of(10L, 11L), catalog.zonesOfLocality(1L));
    }

    @Test
    @DisplayName("Un barrio nuevo de Users queda asociado a su localidad después del refresco")
    void picksUpNewZonesAfterRefresh() {
        stubCatalog();
        LocationCatalog catalog = catalog(Duration.ZERO);
        catalog.localityOfZone(10L);

        when(client.findZones()).thenReturn(List.of(
                new LocationCatalogClient.Zone(10L, "Cedritos", "Usaquén"),
                new LocationCatalogClient.Zone(12L, "Barrio nuevo", "Usaquén")));
        catalog.localityOfZone(10L);

        assertEquals(Optional.of(1L), catalog.localityOfZone(12L));
    }

    @Test
    @DisplayName("Sin datos previos y con Users caído, la búsqueda se degrada y la validación falla")
    void coldStartWithUsersDown() {
        when(client.findZones()).thenThrow(usersDown());
        LocationCatalog catalog = catalog(Duration.ofMinutes(10));

        assertEquals(Optional.empty(), catalog.localityOfZone(10L));
        assertEquals(Set.of(), catalog.zonesOfLocality(1L));
        assertThrows(ExternalServiceUnavailableException.class, () -> catalog.requireLocality(1L));
    }
}