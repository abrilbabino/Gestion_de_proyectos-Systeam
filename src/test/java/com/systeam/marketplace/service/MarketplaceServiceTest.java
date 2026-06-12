package com.systeam.marketplace.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.systeam.blockchain.service.BlockchainService;
import com.systeam.blockchain.service.IdeaMarketplaceService;
import com.systeam.marketplace.dto.CreateListingRequest;
import com.systeam.marketplace.dto.ListingResponse;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.tokenization.service.SubtokenService;

@ExtendWith(MockitoExtension.class)
class MarketplaceServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final Long BUYER_ID = 2L;
    private static final Long SUBTOKEN_ID = 10L;
    private static final Long LISTING_ID = 100L;
    private static final BigInteger PRECIO_UNITARIO = new BigInteger("200000000000000000000");
    private static final BigInteger CANTIDAD = BigInteger.valueOf(10);

    private MarketplaceService service;

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private IdeaMarketplaceService ideaMarketplaceService;

    @Mock
    private SubtokenService subtokenService;

    @Mock
    private BlockchainService blockchainService;

    private CreateListingRequest createRequest;

    @BeforeEach
    void setUp() {
        service = new MarketplaceService(jdbc, ideaMarketplaceService, subtokenService, blockchainService);

        createRequest = new CreateListingRequest();
        createRequest.setSubtokenId(SUBTOKEN_ID);
        createRequest.setCantidad(CANTIDAD);
        createRequest.setPrecioUnitario(PRECIO_UNITARIO);
    }

    // ═════════════════════════════════════════════════════
    // createListing
    // ═════════════════════════════════════════════════════

    @Test
    void createListing_cuandoUsuarioNoExiste_lanzaResourceNotFound() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(SELLER_ID)))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.createListing(SELLER_ID, createRequest))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Usuario");
    }

    @Test
    void createListing_cuandoSubtokenNoExiste_lanzaResourceNotFound() {
        mockUserFound();
        when(jdbc.query(anyString(), any(RowMapper.class), eq(SUBTOKEN_ID)))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.createListing(SELLER_ID, createRequest))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Sub-token");
    }

    @Test
    void createListing_cuandoPrecioMenorAlBase_lanzaConflict() {
        mockUserFound();
        mockSubtokenFound();
        BigDecimal precioBase = BigDecimal.TEN;
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(SUBTOKEN_ID)))
            .thenReturn(precioBase);

        createRequest.setPrecioUnitario(BigInteger.ONE);

        assertThatThrownBy(() -> service.createListing(SELLER_ID, createRequest))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("precio base");
    }

    @Test
    void createListing_cuandoSaldoInsuficiente_lanzaConflict() {
        mockUserFound();
        mockSubtokenFound();
        mockPrecioBaseValido();

        when(jdbc.queryForObject(anyString(), eq(BigInteger.class), eq(SELLER_ID), eq(SUBTOKEN_ID)))
            .thenReturn(BigInteger.valueOf(5));

        assertThatThrownBy(() -> service.createListing(SELLER_ID, createRequest))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("suficientes");
    }

    @Test
        void createListing_retornaListingResponse() throws Exception {
            mockUserFound();
            mockSubtokenFound();
            mockPrecioBaseValido();
            mockPortfolioSuficiente();

            createRequest.setTxHash("0xtxhash");
            when(blockchainService.verifyTransaction("0xtxhash")).thenReturn(true);

            when(jdbc.queryForObject(anyString(), eq(Long.class), eq(SELLER_ID), eq(SUBTOKEN_ID),
                eq(CANTIDAD), eq(CANTIDAD), eq(PRECIO_UNITARIO), eq("0xtxhash")))
                .thenReturn(LISTING_ID);

            mockGetListingById(LISTING_ID);

            ListingResponse result = service.createListing(SELLER_ID, createRequest);

            assertThat(result.getId()).isEqualTo(LISTING_ID);
            assertThat(result.getEstado()).isEqualTo("ACTIVE");
            verify(jdbc).update(anyString(), eq(CANTIDAD), eq(SELLER_ID), eq(SUBTOKEN_ID));
        }

    // ═════════════════════════════════════════════════════
    // buyFromListing
    // ═════════════════════════════════════════════════════

    @Test
    void buyFromListing_cuandoListingNoExiste_lanzaResourceNotFound() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(LISTING_ID)))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.buyFromListing(BUYER_ID, LISTING_ID, CANTIDAD, "0xmocktx"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Orden");
    }

    @Test
    void buyFromListing_cuandoNoActiva_lanzaConflict() {
        mockFindListingOrThrow("CANCELLED", 50);

        assertThatThrownBy(() -> service.buyFromListing(BUYER_ID, LISTING_ID, CANTIDAD, "0xmocktx"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("no esta activa");
    }

    @Test
    void buyFromListing_cuandoSelfBuy_lanzaConflict() {
        mockFindListingOrThrow("ACTIVE", 50);

        assertThatThrownBy(() -> service.buyFromListing(SELLER_ID, LISTING_ID, CANTIDAD, "0xmocktx"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("tus propios");
    }

    @Test
    void buyFromListing_cuandoExcedeDisponible_lanzaConflict() {
        mockFindListingOrThrow("ACTIVE", 5);

        assertThatThrownBy(() -> service.buyFromListing(BUYER_ID, LISTING_ID, CANTIDAD, "0xmocktx"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("excede la disponible");
    }

    @Test
    void buyFromListing_cuandoSaldoInsuficiente_lanzaConflict() {
        mockFindListingOrThrow("ACTIVE", 50);
        when(jdbc.query(argThat(s -> s != null && s.toString().contains("FROM users")), any(RowMapper.class), eq(BUYER_ID)))
            .thenReturn(List.of(Map.of("id", BUYER_ID, "nombre", "Buyer", "saldo_idea", BigDecimal.ONE)));

        assertThatThrownBy(() -> service.buyFromListing(BUYER_ID, LISTING_ID, CANTIDAD, "0xmocktx"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Saldo insuficiente");
    }

    @Test
    void buyFromListing_retornaListingResponse() throws Exception {
        mockFindListingOrThrow("ACTIVE", 50);

        when(blockchainService.verifyTransaction("0xbuytx")).thenReturn(true);

        // Buyer tiene saldo suficiente para pagar PRECIO_UNITARIO * CANTIDAD
        when(jdbc.query(argThat(s -> s != null && s.toString().contains("FROM users")), any(RowMapper.class), eq(BUYER_ID)))
            .thenReturn(List.of(Map.of("id", BUYER_ID, "nombre", "Buyer",
                "saldo_idea", new BigDecimal("999999999999999999999999999"))));

        mockGetListingById(LISTING_ID);

        ListingResponse result = service.buyFromListing(BUYER_ID, LISTING_ID, CANTIDAD, "0xbuytx");

        assertThat(result.getId()).isEqualTo(LISTING_ID);
        verify(jdbc).update(anyString(), any(), eq(BUYER_ID));
        verify(jdbc).update(anyString(), any(), eq(SELLER_ID));
        verify(subtokenService).addPortfolioEntry(eq(BUYER_ID), eq(SUBTOKEN_ID), anyInt());
    }

    // ═════════════════════════════════════════════════════
    // cancelListing
    // ═════════════════════════════════════════════════════

    @Test
    void cancelListing_cuandoNoEsPropietario_lanzaConflict() {
        mockFindListingOrThrow("ACTIVE", 10);

        assertThatThrownBy(() -> service.cancelListing(BUYER_ID, LISTING_ID, "0xmocktx"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("no te pertenece");
    }

    @Test
    void cancelListing_cuandoYaNoEstaActiva_lanzaConflict() {
        mockFindListingOrThrow("EXECUTED", 10);

        assertThatThrownBy(() -> service.cancelListing(SELLER_ID, LISTING_ID, "0xmocktx"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("ya no esta activa");
    }

    @Test
    void cancelListing_ejecutaCorrectamente() throws Exception {
        mockFindListingOrThrow("ACTIVE", 10);
        when(blockchainService.verifyTransaction("0xcanceltx")).thenReturn(true);

        service.cancelListing(SELLER_ID, LISTING_ID, "0xcanceltx");

        verify(jdbc).update(anyString(), eq(LISTING_ID));
        verify(subtokenService).addPortfolioEntry(eq(SELLER_ID), eq(SUBTOKEN_ID), eq(10));
    }

    // ═════════════════════════════════════════════════════
    // listActiveListings
    // ═════════════════════════════════════════════════════

    @Test
    void listActiveListings_retornaPaginaVacia() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt(), any()))
            .thenReturn(List.of());

        Page<ListingResponse> page = service.listActiveListings(PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void listActiveListings_retornaPaginaConResultados() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        ListingResponse listing = new ListingResponse();
        listing.setId(LISTING_ID);
        listing.setEstado("ACTIVE");
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt(), any()))
            .thenReturn(List.of(listing));

        Page<ListingResponse> page = service.listActiveListings(PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(LISTING_ID);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    // ═════════════════════════════════════════════════════
    // listListingsBySubtoken
    // ═════════════════════════════════════════════════════

    @Test
    void listListingsBySubtoken_retornaLista() {
        ListingResponse listing = new ListingResponse();
        listing.setId(LISTING_ID);
        listing.setSubtokenId(SUBTOKEN_ID);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(SUBTOKEN_ID)))
            .thenReturn(List.of(listing));

        var results = service.listListingsBySubtoken(SUBTOKEN_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSubtokenId()).isEqualTo(SUBTOKEN_ID);
    }

    // ═════════════════════════════════════════════════════
    // getListingById
    // ═════════════════════════════════════════════════════

    @Test
    void getListingById_cuandoNoExiste_lanzaResourceNotFound() {
        when(jdbc.query(argThat(s -> s.toString().contains("ob.on_chain_id")), any(RowMapper.class), eq(LISTING_ID)))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.getListingById(LISTING_ID))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Orden no encontrada");
    }

    @Test
    void getListingById_retornaListing() {
        mockGetListingById(LISTING_ID);

        ListingResponse result = service.getListingById(LISTING_ID);

        assertThat(result.getId()).isEqualTo(LISTING_ID);
        assertThat(result.getEstado()).isEqualTo("ACTIVE");
    }

    // ═════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════

    private Map<String, Object> sellerMap() {
        return Map.of("id", SELLER_ID, "nombre", "Seller", "saldo_idea", new BigDecimal("5000"));
    }

    private Map<String, Object> subtokenMap() {
        return Map.of("id", SUBTOKEN_ID, "proyecto_id", 5L, "contract_address",
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }

    private Map<String, Object> listingMap(String estado, int cantidad) {
        return Map.of(
            "id", LISTING_ID,
            "seller_id", SELLER_ID,
            "subtoken_id", SUBTOKEN_ID,
            "cantidad", BigInteger.valueOf(cantidad),
            "precio_unitario", PRECIO_UNITARIO,
            "estado", estado
        );
    }

    private void mockUserFound() {
        when(jdbc.query(argThat(s -> s != null && s.toString().contains("FROM users")),
            any(RowMapper.class), eq(SELLER_ID)))
            .thenReturn(List.of(sellerMap()));
    }

    private void mockSubtokenFound() {
        when(jdbc.query(argThat(s -> s != null && s.toString().contains("FROM subtokens")),
            any(RowMapper.class), eq(SUBTOKEN_ID)))
            .thenReturn(List.of(subtokenMap()));
    }

    private void mockPrecioBaseValido() {
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(SUBTOKEN_ID)))
            .thenReturn(BigDecimal.ONE);
    }

    private void mockPortfolioSuficiente() {
        when(jdbc.queryForObject(anyString(), eq(BigInteger.class), eq(SELLER_ID), eq(SUBTOKEN_ID)))
            .thenReturn(BigInteger.valueOf(100));
    }

    private void mockFindListingOrThrow(String estado, int cantidad) {
        when(jdbc.query(
            argThat(s -> s != null && s.toString().contains("FROM order_book") && !s.toString().contains("ob.")),
            any(RowMapper.class), eq(LISTING_ID)))
            .thenReturn(List.of(listingMap(estado, cantidad)));
    }

    private void mockGetListingById(Long id) {
        ListingResponse listing = new ListingResponse();
        listing.setId(id);
        listing.setSellerId(SELLER_ID);
        listing.setSubtokenId(SUBTOKEN_ID);
        listing.setCantidad(BigInteger.valueOf(50));
        listing.setCantidadInicial(BigInteger.valueOf(50));
        listing.setPrecioUnitario(PRECIO_UNITARIO);
        listing.setEstado("ACTIVE");
        listing.setSellerName("Seller");
        listing.setProjectName("Projecto");
        when(jdbc.query(
            argThat(s -> s != null && s.toString().contains("ob.on_chain_id")),
            any(RowMapper.class), eq(id)))
            .thenReturn(List.of(listing));
    }
}
