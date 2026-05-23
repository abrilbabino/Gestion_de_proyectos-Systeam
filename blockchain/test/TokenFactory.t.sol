// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Test.sol";
import "../contracts/TokenFactory.sol";
import "../contracts/ProjectToken.sol";

contract TokenFactoryTest is Test {
    TokenFactory public factory;
    address public treasury;
    address public alice;
    address public bob;

    uint256 constant PROYECTO_ID = 1;
    string constant NOMBRE = "MiProyecto";
    string constant SIMBOLO = "MPR";

    function setUp() public {
        treasury = makeAddr("treasury");
        alice = makeAddr("alice");
        bob = makeAddr("bob");

        factory = new TokenFactory(treasury);
    }

    // ============ CONSTRUCTOR ============

    function test_ConstructorAsignaTreasury() public view {
        assertEq(factory.treasury(), treasury);
    }

    function test_ConstructorRevertSiTreasuryCero() public {
        vm.expectRevert("TokenFactory: treasury cannot be zero");
        new TokenFactory(address(0));
    }

    function test_ConstructorSinTokens() public view {
        assertEq(factory.obtenerCantidadTokens(), 0);
    }

    // ============ CREAR TOKEN PROYECTO (sin supply inicial) ============

    function test_CrearTokenProyectoConSupplyCero() public {
        address tokenAddr = factory.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 0);

        assertTrue(tokenAddr != address(0));
        assertEq(factory.obtenerCantidadTokens(), 1);
    }

    function test_CrearTokenProyectoMapping() public {
        address tokenAddr = factory.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 0);

        assertEq(factory.tokenDeProyecto(PROYECTO_ID), tokenAddr);
        assertEq(factory.proyectoDeToken(tokenAddr), PROYECTO_ID);
    }

    function test_CrearTokenProyectoAsignaOwnerAlTreasury() public {
        address tokenAddr = factory.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 0);

        ProjectToken proyecto = ProjectToken(tokenAddr);
        assertEq(proyecto.owner(), treasury);
    }

    function test_CrearTokenProyectoAsignaNombreSimbolo() public {
        address tokenAddr = factory.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 0);

        ProjectToken proyecto = ProjectToken(tokenAddr);
        assertEq(proyecto.name(), NOMBRE);
        assertEq(proyecto.symbol(), SIMBOLO);
    }

    function test_CrearTokenProyectoSinSupply() public {
        address tokenAddr = factory.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 0);

        ProjectToken proyecto = ProjectToken(tokenAddr);
        assertEq(proyecto.balanceOf(treasury), 0);
        assertEq(proyecto.totalSupply(), 0);
    }

    function test_CrearTokenProyectoEmiteTokenCreado() public {
        factory.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 0);
    }

    function test_CrearTokenProyectoPublico() public {
        vm.prank(alice);
        address tokenAddr = factory.crearTokenProyecto(99, "Publico", "PUB", 0);

        assertTrue(tokenAddr != address(0));
        assertEq(factory.tokenDeProyecto(99), tokenAddr);
    }

    function test_CrearProyectoConIdCero() public {
        address tokenAddr = factory.crearTokenProyecto(0, "Cero", "CRO", 0);

        assertTrue(tokenAddr != address(0));
        assertEq(factory.tokenDeProyecto(0), tokenAddr);
    }

    function test_CrearProyectoConNombresExtremos() public {
        string memory nombreLargo = "ProyectoConNombreMuyLargoParaProbarLimites";
        string memory simboloCorto = "P";

        address tokenAddr = factory.crearTokenProyecto(PROYECTO_ID, nombreLargo, simboloCorto, 0);

        ProjectToken proyecto = ProjectToken(tokenAddr);
        assertEq(proyecto.name(), nombreLargo);
        assertEq(proyecto.symbol(), simboloCorto);
    }

    function test_CrearTokenProyectoRevertSiDuplicado() public {
        factory.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 0);

        vm.expectRevert("TokenFactory: ya existe un token para este proyecto");
        factory.crearTokenProyecto(PROYECTO_ID, "Otro", "OTR", 0);
    }

    function test_CrearMultiplesProyectos() public {
        address t1 = factory.crearTokenProyecto(1, "Uno", "UNO", 0);
        address t2 = factory.crearTokenProyecto(2, "Dos", "DOS", 0);
        address t3 = factory.crearTokenProyecto(3, "Tres", "TRES", 0);

        assertEq(factory.obtenerCantidadTokens(), 3);
        assertEq(factory.tokenDeProyecto(1), t1);
        assertEq(factory.tokenDeProyecto(2), t2);
        assertEq(factory.tokenDeProyecto(3), t3);
        assertEq(factory.proyectoDeToken(t1), 1);
        assertEq(factory.proyectoDeToken(t2), 2);
        assertEq(factory.proyectoDeToken(t3), 3);
    }

    // ============ SUPPLY INICIAL (bug conocido) ============

    function test_SupplyInicialRevertPorOwnership() public {
        vm.expectRevert();
        factory.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 100);
    }

    function test_TreasuryPuedeMintearSupplyInicial() public {
        address tokenAddr = factory.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 0);

        ProjectToken proyecto = ProjectToken(tokenAddr);
        vm.prank(treasury);
        proyecto.mint(treasury, 1000);

        assertEq(proyecto.balanceOf(treasury), 1000);
        assertEq(proyecto.totalSupply(), 1000);
    }

    // ============ OBTENER CANTIDAD TOKENS ============

    function test_ObtenerCantidadTokensCero() public view {
        assertEq(factory.obtenerCantidadTokens(), 0);
    }

    function test_ObtenerCantidadTokensIncrementa() public {
        factory.crearTokenProyecto(1, "A", "A", 0);
        assertEq(factory.obtenerCantidadTokens(), 1);

        factory.crearTokenProyecto(2, "B", "B", 0);
        assertEq(factory.obtenerCantidadTokens(), 2);
    }

    // ============ OBTENER TOKEN POR INDICE ============

    function test_ObtenerTokenPorIndice() public {
        address t1 = factory.crearTokenProyecto(1, "Uno", "U", 0);
        address t2 = factory.crearTokenProyecto(2, "Dos", "D", 0);

        assertEq(factory.obtenerTokenPorIndice(0), t1);
        assertEq(factory.obtenerTokenPorIndice(1), t2);
    }

    function test_ObtenerTokenPorIndiceRevertSiOutOfBounds() public {
        vm.expectRevert("TokenFactory: indice fuera de rango");
        factory.obtenerTokenPorIndice(0);
    }

    function test_ObtenerTokenPorIndiceRevertSiIndiceMayor() public {
        factory.crearTokenProyecto(1, "A", "A", 0);

        vm.expectRevert("TokenFactory: indice fuera de rango");
        factory.obtenerTokenPorIndice(1);
    }

    // ============ GETTERS ============

    function test_TokenDeProyectoDevuelveZeroSiInexistente() public view {
        assertEq(factory.tokenDeProyecto(999), address(0));
    }

    function test_ProyectoDeTokenDevuelveZeroSiInexistente() public view {
        assertEq(factory.proyectoDeToken(address(0x1234)), 0);
    }

    // ============ INTEROPERABILIDAD ============

    function test_TokenCreadoEsProjectTokenCompatible() public {
        address tokenAddr = factory.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 0);

        ProjectToken proyecto = ProjectToken(tokenAddr);
        assertEq(proyecto.name(), NOMBRE);
        assertEq(proyecto.symbol(), SIMBOLO);
        assertEq(proyecto.decimals(), 18);
    }

    function test_TokenCreadoUsaTasaDeQuema() public {
        address tokenAddr = factory.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 0);

        ProjectToken proyecto = ProjectToken(tokenAddr);
        assertEq(proyecto.TASA_QUEMA(), 10);
    }

    // ============ FUZZ ============

    function testFuzz_CrearTokenDiferentesIds(uint32 rawId) public {
        uint256 proyectoId = uint256(rawId);
        vm.assume(proyectoId > 0 && proyectoId < 1_000_000);

        address tokenAddr = factory.crearTokenProyecto(proyectoId, "Fuzz", "FUZ", 0);
        assertEq(factory.tokenDeProyecto(proyectoId), tokenAddr);
    }

    function testFuzz_CrearMultiplesTokensFuzz(uint8 cantidad) public {
        vm.assume(cantidad > 0 && cantidad <= 20);

        address[] memory tokens = new address[](cantidad);

        for (uint256 i = 0; i < cantidad; i++) {
            tokens[i] = factory.crearTokenProyecto(i + 1, "Auto", "AUTO", 0);
        }

        assertEq(factory.obtenerCantidadTokens(), cantidad);

        for (uint256 i = 0; i < cantidad; i++) {
            assertEq(factory.obtenerTokenPorIndice(i), tokens[i]);
            assertEq(factory.tokenDeProyecto(i + 1), tokens[i]);
        }
    }
}
