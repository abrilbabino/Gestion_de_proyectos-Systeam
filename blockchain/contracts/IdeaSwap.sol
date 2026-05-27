// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

contract IdeaSwap is AccessControl, ReentrancyGuard {

    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");
    bytes32 public constant LIQUIDITY_PROVIDER_ROLE = keccak256("LIQUIDITY_PROVIDER_ROLE");

    IERC20 public immutable idea;
    IERC20 public immutable usdc;

    uint256 public feeBps = 100;
    uint256 public ideaReserve;
    uint256 public usdcReserve;

    event Swapped(
        address indexed user,
        uint256 ideaAmount,
        uint256 usdcAmount,
        bool ideaToUsdc
    );
    event LiquidityAdded(uint256 ideaAmount, uint256 usdcAmount);
    event LiquidityRemoved(uint256 ideaAmount, uint256 usdcAmount);

    constructor(address _idea, address _usdc) {
        require(_idea != address(0), "Swap: invalid IDEA");
        require(_usdc != address(0), "Swap: invalid USDC");
        idea = IERC20(_idea);
        usdc = IERC20(_usdc);
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(ADMIN_ROLE, msg.sender);
        _grantRole(LIQUIDITY_PROVIDER_ROLE, msg.sender);
    }

    function addLiquidity(uint256 ideaAmount, uint256 usdcAmount)
        external
        onlyRole(LIQUIDITY_PROVIDER_ROLE)
        nonReentrant
    {
        require(ideaAmount > 0 || usdcAmount > 0, "Swap: amounts > 0");

        if (ideaAmount > 0) {
            require(idea.transferFrom(msg.sender, address(this), ideaAmount),
                "Swap: IDEA transfer failed");
            ideaReserve += ideaAmount;
        }
        if (usdcAmount > 0) {
            require(usdc.transferFrom(msg.sender, address(this), usdcAmount),
                "Swap: USDC transfer failed");
            usdcReserve += usdcAmount;
        }

        emit LiquidityAdded(ideaAmount, usdcAmount);
    }

    function removeLiquidity(uint256 ideaAmount, uint256 usdcAmount)
        external
        onlyRole(LIQUIDITY_PROVIDER_ROLE)
        nonReentrant
    {
        require(ideaAmount > 0 || usdcAmount > 0, "Swap: amounts > 0");
        require(ideaReserve >= ideaAmount, "Swap: insufficient IDEA");
        require(usdcReserve >= usdcAmount, "Swap: insufficient USDC");

        ideaReserve -= ideaAmount;
        usdcReserve -= usdcAmount;

        if (ideaAmount > 0) {
            require(idea.transfer(msg.sender, ideaAmount), "Swap: IDEA transfer failed");
        }
        if (usdcAmount > 0) {
            require(usdc.transfer(msg.sender, usdcAmount), "Swap: USDC transfer failed");
        }

        emit LiquidityRemoved(ideaAmount, usdcAmount);
    }

    function swapUsdcForExactIdea(uint256 ideaOut) external nonReentrant {
        require(ideaOut > 0, "Swap: output > 0");
        require(ideaReserve >= ideaOut, "Swap: insufficient IDEA reserve");

        uint256 usdcIn = (ideaOut * usdcReserve) / ideaReserve;
        uint256 fee = (usdcIn * feeBps) / 10000;
        uint256 usdcInTotal = usdcIn + fee;

        require(usdc.transferFrom(msg.sender, address(this), usdcInTotal),
            "Swap: USDC transfer failed");

        ideaReserve -= ideaOut;
        usdcReserve += usdcIn;

        require(idea.transfer(msg.sender, ideaOut), "Swap: IDEA transfer failed");

        emit Swapped(msg.sender, ideaOut, usdcInTotal, false);
    }

    function swapIdeaForExactUsdc(uint256 usdcOut) external nonReentrant {
        require(usdcOut > 0, "Swap: output > 0");
        require(usdcReserve >= usdcOut, "Swap: insufficient USDC reserve");

        uint256 ideaIn = (usdcOut * ideaReserve) / usdcReserve;
        uint256 fee = (ideaIn * feeBps) / 10000;
        uint256 ideaInTotal = ideaIn + fee;

        require(idea.transferFrom(msg.sender, address(this), ideaInTotal),
            "Swap: IDEA transfer failed");

        usdcReserve -= usdcOut;
        ideaReserve += ideaIn;

        require(usdc.transfer(msg.sender, usdcOut), "Swap: USDC transfer failed");

        emit Swapped(msg.sender, ideaInTotal, usdcOut, true);
    }

    function getAmountsOut(uint256 amountIn, bool ideaToUsdc)
        external
        view
        returns (uint256 amountOut, uint256 fee)
    {
        if (ideaToUsdc) {
            amountOut = (amountIn * usdcReserve) / ideaReserve;
        } else {
            amountOut = (amountIn * ideaReserve) / usdcReserve;
        }
        fee = (amountIn * feeBps) / 10000;
    }

    function updateFee(uint256 newFeeBps) external onlyRole(ADMIN_ROLE) {
        require(newFeeBps <= 1000, "Swap: fee max 10%");
        feeBps = newFeeBps;
    }
}
