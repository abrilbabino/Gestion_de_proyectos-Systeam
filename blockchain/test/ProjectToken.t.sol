// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Test.sol";
import "../contracts/ProjectToken.sol";

contract ProjectTokenTest is Test {
    ProjectToken public token;
    address public dueno;
    address public alice;
    address public bob;

    uint256 constant MONTO_MINT = 1_000_000e18;

    function setUp() public {
        dueno = address(this);
        alice = makeAddr("alice");
        bob = makeAddr("bob");

        token = new ProjectToken("ProyectoTest", "PTT", dueno);
        token.mint(alice, MONTO_MINT);
    }

    // ============ CONSTRUCTOR ============

    function test_ConstructorAsignaNombre() public view {
        assertEq(token.name(), "ProyectoTest");
    }

    function test_ConstructorAsignaSimbolo() public view {
        assertEq(token.symbol(), "PTT");
    }

    function test_ConstructorAsignaOwner() public view {
        assertEq(token.owner(), dueno);
    }

    function test_ConstructorTasaQuema() public view {
        assertEq(token.TASA_QUEMA(), 10);
    }

    function test_ConstructorRevertSiOwnerCero() public {
        vm.expectRevert();
        new ProjectToken("Fail", "F", address(0));
    }

    function test_ConstructorSupplyCero() public view {
        assertEq(token.totalSupply(), MONTO_MINT);
    }

    // ============ MINT ============

    function test_MintExitoso() public {
        token.mint(bob, 500e18);

        assertEq(token.balanceOf(bob), 500e18);
        assertEq(token.totalSupply(), MONTO_MINT + 500e18);
    }

    function test_MintEmiteTokensMinteados() public {
        token.mint(bob, 500e18);
    }

    function test_MintRevertSiNoOwner() public {
        vm.prank(alice);
        vm.expectRevert();
        token.mint(bob, 500e18);
    }

    function test_MintRevertSiDestinoCero() public {
        vm.expectRevert();
        token.mint(address(0), 500e18);
    }

    function test_MintConCero() public {
        token.mint(bob, 0);

        assertEq(token.balanceOf(bob), 0);
        assertEq(token.totalSupply(), MONTO_MINT);
    }

    function test_MintNoAplicaQuema() public {
        uint256 supplyAntes = token.totalSupply();
        token.mint(alice, 1000e18);

        uint256 saldoAlice = token.balanceOf(alice);
        assertEq(saldoAlice, MONTO_MINT + 1000e18);
        assertEq(token.totalSupply(), supplyAntes + 1000e18);
    }

    function test_MintMultiple() public {
        token.mint(alice, 100e18);
        token.mint(bob, 200e18);
        token.mint(alice, 300e18);

        assertEq(token.balanceOf(alice), MONTO_MINT + 400e18);
        assertEq(token.balanceOf(bob), 200e18);
        assertEq(token.totalSupply(), MONTO_MINT + 600e18);
    }

    function test_MintGranCantidad() public {
        uint256 granMonto = 1_000_000_000e18;
        token.mint(alice, granMonto);
        assertEq(token.balanceOf(alice), MONTO_MINT + granMonto);
    }

    // ============ BURN FROM ============

    function test_BurnFromExitoso() public {
        token.burnFrom(alice, 100e18);

        assertEq(token.balanceOf(alice), MONTO_MINT - 100e18);
        assertEq(token.totalSupply(), MONTO_MINT - 100e18);
    }

    function test_BurnFromEmiteTokensQuemados() public {
        token.burnFrom(alice, 100e18);
    }

    function test_BurnFromRevertSiNoOwner() public {
        vm.prank(alice);
        vm.expectRevert();
        token.burnFrom(alice, 100e18);
    }

    function test_BurnFromRevertSiSaldoInsuficiente() public {
        vm.expectRevert();
        token.burnFrom(alice, MONTO_MINT + 1);
    }

    function test_BurnFromRevertSiDesdeCero() public {
        vm.expectRevert();
        token.burnFrom(address(0), 100e18);
    }

    function test_BurnFromConCero() public {
        token.burnFrom(alice, 0);
        assertEq(token.balanceOf(alice), MONTO_MINT);
    }

    function test_BurnFromBurnTodo() public {
        token.burnFrom(alice, MONTO_MINT);
        assertEq(token.balanceOf(alice), 0);
        assertEq(token.totalSupply(), 0);
    }

    function test_BurnFromNoAplicaQuema() public {
        uint256 supplyAntes = token.totalSupply();
        token.burnFrom(alice, 1000e18);

        assertEq(token.balanceOf(alice), MONTO_MINT - 1000e18);
        assertEq(token.totalSupply(), supplyAntes - 1000e18);
    }

    // ============ TRANSFER (UPDATE) ============

    function test_TransferSinQuemaPorMontoBajo() public {
        uint256 monto = 999;

        vm.prank(alice);
        token.transfer(bob, monto);

        assertEq(token.balanceOf(alice), MONTO_MINT - monto);
        assertEq(token.balanceOf(bob), monto);
        assertEq(token.totalSupply(), MONTO_MINT);
    }

    function test_TransferConQuema() public {
        uint256 monto = 10000;
        uint256 esperadoQuemado = monto * 10 / 10000;
        uint256 esperadoEnvio = monto - esperadoQuemado;

        vm.prank(alice);
        token.transfer(bob, monto);

        assertEq(token.balanceOf(alice), MONTO_MINT - monto);
        assertEq(token.balanceOf(bob), esperadoEnvio);
        assertEq(token.totalSupply(), MONTO_MINT - esperadoQuemado);
    }

    function test_TransferConQuemaEmiteTokensQuemados() public {
        uint256 monto = 10000;

        vm.prank(alice);
        token.transfer(bob, monto);
    }

    function test_TransferMultiplesVeces() public {
        vm.prank(alice);
        token.transfer(bob, 50000);

        uint256 quema1 = 50000 * 10 / 10000;
        assertEq(token.balanceOf(bob), 50000 - quema1);

        vm.prank(bob);
        token.transfer(alice, 20000);

        uint256 quema2 = 20000 * 10 / 10000;
        assertEq(token.balanceOf(alice), MONTO_MINT - 50000 + (20000 - quema2));
        assertEq(token.totalSupply(), MONTO_MINT - quema1 - quema2);
    }

    function test_TransferQuemaExacta() public {
        uint256 monto = 100000;
        uint256 quema = monto * 10 / 10000;
        uint256 envio = monto - quema;

        vm.prank(alice);
        token.transfer(bob, monto);

        assertEq(quema, 100);
        assertEq(envio, 99900);
        assertEq(token.balanceOf(bob), 99900);
    }

    function test_TransferDesdeAliceABobYViceversa() public {
        uint256 ida = 500000e18;
        uint256 quemaIda = ida * 10 / 10000;

        vm.prank(alice);
        token.transfer(bob, ida);

        uint256 saldoBob = ida - quemaIda;
        assertEq(token.balanceOf(bob), saldoBob);

        uint256 vuelta = 100000e18;
        uint256 quemaVuelta = vuelta * 10 / 10000;

        vm.prank(bob);
        token.transfer(alice, vuelta);

        uint256 saldoFinalAlice = MONTO_MINT - ida + (vuelta - quemaVuelta);
        assertEq(token.balanceOf(alice), saldoFinalAlice);
        assertEq(token.balanceOf(bob), saldoBob - vuelta);
    }

    function test_TransferConMontoJustoAlLimite() public {
        uint256 exacto = 1000;
        uint256 quema = exacto * 10 / 10000;
        assertEq(quema, 1);

        vm.prank(alice);
        token.transfer(bob, exacto);

        assertEq(token.balanceOf(bob), exacto - quema);
        assertEq(token.totalSupply(), MONTO_MINT - quema);
    }

    function test_TransferConZero() public {
        vm.prank(alice);
        token.transfer(bob, 0);

        assertEq(token.balanceOf(alice), MONTO_MINT);
        assertEq(token.balanceOf(bob), 0);
    }

    function test_TransferRevertSiSaldoInsuficiente() public {
        vm.prank(bob);
        vm.expectRevert();
        token.transfer(alice, 1);
    }

    function test_TransferRevertSiDestinoCero() public {
        vm.prank(alice);
        vm.expectRevert();
        token.transfer(address(0), 100);
    }

    // ============ TRANSFER FROM ============

    function test_TransferFromConQuema() public {
        vm.prank(alice);
        token.approve(bob, 50000);

        uint256 monto = 50000;
        uint256 quema = monto * 10 / 10000;
        uint256 envio = monto - quema;

        vm.prank(bob);
        token.transferFrom(alice, bob, monto);

        assertEq(token.balanceOf(alice), MONTO_MINT - monto);
        assertEq(token.balanceOf(bob), envio);
    }

    function test_TransferFromRevertSiAllowanceInsuficiente() public {
        vm.prank(bob);
        vm.expectRevert();
        token.transferFrom(alice, bob, 100);
    }

    // ============ QUEMA NO APLICA EN MINT/BURN ============

    function test_MintNoQuema() public {
        uint256 supplyAntes = token.totalSupply();
        token.mint(bob, 100000e18);
        assertEq(token.balanceOf(bob), 100000e18);
        assertEq(token.totalSupply(), supplyAntes + 100000e18);
    }

    function test_BurnNoQuema() public {
        uint256 supplyAntes = token.totalSupply();
        token.burnFrom(alice, 100000e18);
        assertEq(token.totalSupply(), supplyAntes - 100000e18);
    }

    // ============ BURN TAX PRECISION ============

    function test_QuemaCalculoCorrecto() public {
        uint256 monto = 999999;
        uint256 quemaEsperada = monto * 10 / 10000;

        vm.prank(alice);
        token.transfer(bob, monto);

        assertEq(token.balanceOf(bob), monto - quemaEsperada);
        assertEq(token.totalSupply(), MONTO_MINT - quemaEsperada);
    }

    function test_QuemaSinRedondeo() public {
        uint256 monto = 1000000;
        uint256 quema = monto * 10 / 10000;

        assertEq(quema, 1000);

        vm.prank(alice);
        token.transfer(bob, monto);

        assertEq(token.balanceOf(bob), monto - quema);
        assertEq(token.totalSupply(), MONTO_MINT - quema);
    }

    // ============ OWNERSHIP ============

    function test_TransferOwnership() public {
        token.transferOwnership(alice);
        assertEq(token.owner(), alice);
    }

    function test_SoloNuevoOwnerPuedeMint() public {
        token.transferOwnership(alice);

        vm.prank(alice);
        token.mint(bob, 100e18);

        assertEq(token.balanceOf(bob), 100e18);
    }

    function test_ViejoOwnerNoPuedeMint() public {
        token.transferOwnership(alice);

        vm.prank(dueno);
        vm.expectRevert();
        token.mint(bob, 100e18);
    }

    // ============ FUZZ ============

    function testFuzz_TransferConQuemaProporcional(uint64 montoRaw) public {
        uint256 monto = uint256(montoRaw);
        vm.assume(monto > 0 && monto <= token.balanceOf(alice) / 2);

        uint256 quemaEsperada = monto * 10 / 10000;
        uint256 envioEsperado = monto - quemaEsperada;

        vm.prank(alice);
        token.transfer(bob, monto);

        assertApproxEqAbs(token.balanceOf(bob), envioEsperado, quemaEsperada);
        assertGe(token.balanceOf(alice), 0);
    }

    function testFuzz_MintDiferentesCantidades(uint96 cantidadRaw) public {
        uint256 cantidad = uint256(cantidadRaw);
        vm.assume(cantidad <= 1_000_000_000e18);

        uint256 supplyAntes = token.totalSupply();

        token.mint(bob, cantidad);
        assertEq(token.balanceOf(bob), cantidad);
        assertEq(token.totalSupply(), supplyAntes + cantidad);
    }

    function testFuzz_BurnFromDiferentesCantidades(uint96 cantidadRaw) public {
        uint256 cantidad = uint256(cantidadRaw);
        vm.assume(cantidad <= MONTO_MINT);

        uint256 supplyAntes = token.totalSupply();

        token.burnFrom(alice, cantidad);
        assertEq(token.totalSupply(), supplyAntes - cantidad);
    }

    function testFuzz_TransferEncadenadoSinDesborde(
        uint64 monto1Raw, uint64 monto2Raw, uint64 monto3Raw
    ) public {
        address carlos = makeAddr("carlos");
        token.mint(carlos, 1_000_000e18);

        uint256 monto1 = uint256(monto1Raw) % (token.balanceOf(alice) / 2);
        uint256 monto2 = uint256(monto2Raw) % (token.balanceOf(bob) + 1);
        uint256 monto3 = uint256(monto3Raw) % (token.balanceOf(carlos) / 2);

        vm.assume(monto1 > 0);

        vm.prank(alice);
        token.transfer(bob, monto1);

        uint256 quema1 = monto1 * 10 / 10000;
        uint256 saldoBobEsperado = monto1 - quema1;

        if (monto2 > 0 && monto2 <= saldoBobEsperado) {
            vm.prank(bob);
            token.transfer(carlos, monto2);
        }

        if (monto3 > 0 && monto3 <= token.balanceOf(carlos)) {
            vm.prank(carlos);
            token.transfer(alice, monto3);
        }

        assertGe(token.balanceOf(alice), 0);
        assertGe(token.balanceOf(bob), 0);
        assertGe(token.balanceOf(carlos), 0);
        assertGe(token.totalSupply(), 0);
    }
}
