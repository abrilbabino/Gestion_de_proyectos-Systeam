// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Test.sol";
import "@openzeppelin/contracts/proxy/ERC1967/ERC1967Proxy.sol";
import "../contracts/SubToken.sol";

contract SubTokenTest is Test {
    SubToken public implementation;
    SubToken public proxy;
    address public factory = address(0x111);
    address public creator = address(0x222);
    address public alice = address(0x333);
    address public bob = address(0x444);

    uint256 public constant PROYECTO_ID = 1;
    uint256 public constant RUBRO_ID = 2;
    uint256 public constant DIVIDEND_BPS = 500; // 5%
    uint256 public constant SUPPLY_INICIAL = 1_000_000e18;

    function setUp() public {
        implementation = new SubToken();

        bytes memory initData = abi.encodeWithSelector(
            SubToken.initialize.selector,
            PROYECTO_ID,
            RUBRO_ID,
            DIVIDEND_BPS,
            creator,
            factory,
            "Proyecto Test",
            "PRJ1",
            SUPPLY_INICIAL
        );

        vm.prank(factory);
        proxy = SubToken(address(new ERC1967Proxy(address(implementation), initData)));
    }

    function test_InitialState() public view {
        assertEq(proxy.proyectoId(), PROYECTO_ID);
        assertEq(proxy.rubroId(), RUBRO_ID);
        assertEq(proxy.dividendBps(), DIVIDEND_BPS);
        assertEq(proxy.creator(), creator);
        assertEq(proxy.factory(), factory);
    }

    function test_InitialSupplyMintedToFactory() public view {
        assertEq(proxy.balanceOf(factory), SUPPLY_INICIAL);
        assertEq(proxy.totalSupply(), SUPPLY_INICIAL);
    }

    function test_NameAndSymbol() public view {
        assertEq(proxy.name(), "Proyecto Test");
        assertEq(proxy.symbol(), "PRJ1");
    }

    function test_OnlyFactoryCanBurn() public {
        uint256 burnAmount = 100e18;

        vm.prank(alice);
        vm.expectRevert("SubToken: only factory");
        proxy.burnFrom(factory, burnAmount);

        vm.prank(factory);
        proxy.burnFrom(factory, burnAmount);

        assertEq(proxy.balanceOf(factory), SUPPLY_INICIAL - burnAmount);
    }

    function test_OnlyFactoryCanUpgrade() public {
        SubToken newImpl = new SubToken();

        vm.prank(alice);
        vm.expectRevert("SubToken: only factory can upgrade");
        proxy.upgradeToAndCall(address(newImpl), "");

        vm.prank(factory);
        proxy.upgradeToAndCall(address(newImpl), "");
    }

    function test_TransferWorks() public {
        uint256 transferAmount = 1000e18;

        vm.prank(factory);
        proxy.transfer(alice, transferAmount);

        assertEq(proxy.balanceOf(alice), transferAmount);
        assertEq(proxy.balanceOf(factory), SUPPLY_INICIAL - transferAmount);
    }

    function test_NoBurnOnTransfer() public {
        uint256 transferAmount = 1000e18;

        vm.prank(factory);
        proxy.transfer(alice, transferAmount);

        assertEq(proxy.totalSupply(), SUPPLY_INICIAL);
    }

    function test_ConstructorDisablesInitializers() public {
        vm.expectRevert();
        implementation.initialize(
            0, 0, 0, address(0), address(0), "", "", 0
        );
    }

    function test_UpgradePreservesState() public {
        SubToken newImpl = new SubToken();

        vm.prank(factory);
        proxy.upgradeToAndCall(address(newImpl), "");

        assertEq(proxy.proyectoId(), PROYECTO_ID);
        assertEq(proxy.balanceOf(factory), SUPPLY_INICIAL);
        assertEq(proxy.name(), "Proyecto Test");
    }
}
