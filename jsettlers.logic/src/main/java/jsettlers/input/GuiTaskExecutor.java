/*******************************************************************************
 * Copyright (c) 2015 - 2018
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package jsettlers.input;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import jsettlers.common.CommonConstants;
import jsettlers.common.action.EMoveToType;
import jsettlers.common.buildings.IBuilding;
import jsettlers.common.map.shapes.HexGridArea;
import jsettlers.common.movable.EMovableType;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.common.utils.mutables.MutableInt;
import jsettlers.input.tasks.CastSpellGuiTask;
import jsettlers.input.tasks.ChangeTowerSoldiersGuiTask;
import jsettlers.input.tasks.ChangeTradingRequestGuiTask;
import jsettlers.input.tasks.ConstructBuildingTask;
import jsettlers.input.tasks.ConvertGuiTask;
import jsettlers.input.tasks.EGuiAction;
import jsettlers.input.tasks.MovableGuiTask;
import jsettlers.input.tasks.MoveToGuiTask;
import jsettlers.input.tasks.OrderShipGuiTask;
import jsettlers.input.tasks.SetAcceptedStockMaterialGuiTask;
import jsettlers.input.tasks.SetBuildingPriorityGuiTask;
import jsettlers.input.tasks.SetDockGuiTask;
import jsettlers.input.tasks.SetMaterialDistributionSettingsGuiTask;
import jsettlers.input.tasks.SetMaterialPrioritiesGuiTask;
import jsettlers.input.tasks.SetMaterialProductionGuiTask;
import jsettlers.input.tasks.ChangeMovableSettingsTask;
import jsettlers.input.tasks.SetMovableLimitTypeTask;
import jsettlers.input.tasks.SetTradingWaypointGuiTask;
import jsettlers.input.tasks.SimpleBuildingGuiTask;
import jsettlers.input.tasks.SimpleGuiTask;
import jsettlers.input.tasks.UpgradeSoldiersGuiTask;
import jsettlers.input.tasks.WorkAreaGuiTask;
import jsettlers.logic.FerryEntrance;
import jsettlers.logic.buildings.Building;
import jsettlers.logic.buildings.IDockBuilding;
import jsettlers.logic.buildings.military.occupying.OccupyingBuilding;
import jsettlers.logic.buildings.others.StockBuilding;
import jsettlers.logic.buildings.trading.TradingBuilding;
import jsettlers.logic.buildings.workers.DockyardBuilding;
import jsettlers.logic.map.grid.partition.manager.settings.MaterialProductionSettings;
import jsettlers.logic.movable.Movable;
import jsettlers.logic.movable.MovableManager;
import jsettlers.logic.movable.interfaces.IAttackableHumanMovable;
import jsettlers.logic.movable.interfaces.IBearerMovable;
import jsettlers.logic.movable.interfaces.IFerryMovable;
import jsettlers.logic.movable.interfaces.ILogicMovable;
import jsettlers.logic.movable.interfaces.IMageMovable;
import jsettlers.logic.movable.interfaces.IPioneerMovable;
import jsettlers.network.client.task.packets.TaskPacket;
import jsettlers.network.synchronic.timer.ITaskExecutor;

/**
 * @author Andreas Eberle
 */
class GuiTaskExecutor implements ITaskExecutor {
	private static GuiTaskExecutor           instance = null;
	private final  IGuiInputGrid             grid;
	private final  ITaskExecutorGuiInterface guiInterface;
	private final  byte                      playerId;

	GuiTaskExecutor(IGuiInputGrid grid, ITaskExecutorGuiInterface guiInterface, byte playerId) {
		this.grid = grid;
		this.guiInterface = guiInterface;
		this.playerId = playerId;
	}

	public static GuiTaskExecutor get() {
		return instance;
	}

	@Override
	public void executeTask(TaskPacket iTask) {
		if (!(iTask instanceof SimpleGuiTask)) {
			return;
		}

		SimpleGuiTask guiTask = (SimpleGuiTask) iTask;
		if (grid.hasLost(guiTask.getPlayerId()) && !CommonConstants.CONTROL_ALL) { // if player lost, don't execute his tasks any more
			return;
		}

		System.out.println("executeTask(GuiTask): " + guiTask.getGuiAction());
		switch (guiTask.getGuiAction()) {
			case SET_WORK_AREA: {
				setWorkArea((WorkAreaGuiTask) guiTask);
				break;
			}

			case CAST_SPELL:
				castSpell((CastSpellGuiTask) guiTask);
				break;

			case BUILD: {
				ConstructBuildingTask task = (ConstructBuildingTask) guiTask;
				grid.constructBuildingAt(task.getPosition(), task.getType(), task.getPlayerId());
				break;
			}

			case MOVE_TO: {
				MoveToGuiTask task = (MoveToGuiTask) guiTask;
				moveSelectedTo(task.getPosition(), task.getSelection(), task.getMoveToType());
				break;
			}

			case QUICK_SAVE:
				save();
				break;

			case DESTROY_BUILDING: {
				destroyBuilding((SimpleBuildingGuiTask) guiTask);
				break;
			}

			case DESTROY_MOVABLES:
				killSelectedMovables(((MovableGuiTask) guiTask).getSelection());
				break;

			case START_WORKING:
			case STOP_WORKING:
				stopOrStartWorking(((MovableGuiTask) guiTask).getSelection(), guiTask.getGuiAction() == EGuiAction.STOP_WORKING);
				break;

			case CONVERT:
				convertMovables((ConvertGuiTask) guiTask);
				break;

			case SET_BUILDING_PRIORITY:
				setBuildingPriority((SetBuildingPriorityGuiTask) guiTask);
				break;

			case SET_MATERIAL_DISTRIBUTION_SETTINGS: {
				SetMaterialDistributionSettingsGuiTask task = (SetMaterialDistributionSettingsGuiTask) guiTask;
				grid.setMaterialDistributionSettings(task.getManagerPosition(), task.getMaterialType(), task.getBuildingType(), task.getRatio());
				break;
			}

			case SET_MATERIAL_PRIORITIES: {
				SetMaterialPrioritiesGuiTask task = (SetMaterialPrioritiesGuiTask) guiTask;
				grid.setMaterialPrioritiesSettings(task.getManagerPosition(), task.getMaterialTypeForPriority());
			}
			break;

			case UPGRADE_SOLDIERS: {
				UpgradeSoldiersGuiTask task = (UpgradeSoldiersGuiTask) guiTask;
				grid.getPlayer(task.getPlayerId()).getMannaInformation().upgrade(task.getSoldierType());
				break;
			}

			case CHANGE_TRADING: {
				ChangeTradingRequestGuiTask task = (ChangeTradingRequestGuiTask) guiTask;
				ShortPoint2D buildingPos = task.getBuildingPos();
				IBuilding building = grid.getBuildingAt(buildingPos.x, buildingPos.y);
				if (building instanceof TradingBuilding) {
					((TradingBuilding) building).changeRequestedMaterial(task.getMaterial(), task.getAmount(), task.isRelative());
				}
				break;
			}

			case SET_TRADING_WAYPOINT: {
				SetTradingWaypointGuiTask task = (SetTradingWaypointGuiTask) guiTask;
				ShortPoint2D buildingPos = task.getBuildingPos();
				IBuilding building = grid.getBuildingAt(buildingPos.x, buildingPos.y);
				if (building instanceof TradingBuilding) {
					((TradingBuilding) building).setWaypoint(task.getWaypointType(), task.getPosition());
				}
				break;
			}

			case SET_MATERIAL_PRODUCTION: {
				SetMaterialProductionGuiTask task = (SetMaterialProductionGuiTask) guiTask;
				MaterialProductionSettings materialProduction = grid.getMaterialProductionAt(task.getPosition());

				switch (task.getProductionType()) {
					case INCREASE:
						materialProduction.increaseAbsoluteProductionRequest(task.getMaterialType());
						break;
					case DECREASE:
						materialProduction.decreaseAbsoluteProductionRequest(task.getMaterialType());
						break;
					case SET_PRODUCTION:
						materialProduction.setAbsoluteProductionRequest(task.getMaterialType(), (int) task.getRatio());
						break;
					case SET_RATIO:
						materialProduction.setUserConfiguredRelativeRequestValue(task.getMaterialType(), task.getRatio());
						break;
				}
				break;
			}

			case CHANGE_TOWER_SOLDIERS:
				changeTowerSoldiers((ChangeTowerSoldiersGuiTask) guiTask);
				break;

			case SET_ACCEPTED_STOCK_MATERIAL:
				setAcceptedStockMaterial((SetAcceptedStockMaterialGuiTask) guiTask);
				break;

			case SET_DOCK:
				setDock((SetDockGuiTask) guiTask);
				break;

			case ORDER_SHIP:
				orderShip((OrderShipGuiTask) guiTask);
				break;

			case UNLOAD_FERRY:
				unloadFerry((MovableGuiTask) guiTask);
				break;

			case CHANGE_MOVABLE_SETTINGS: {
				ChangeMovableSettingsTask task = (ChangeMovableSettingsTask) guiTask;
				grid.changeMovableSettings(task.getPosition(), task.getMovableType(), task.isRelative(),
						task.getAmount());
				break;
			}

			case SET_MOVABLE_LIMIT_TYPE: {
				SetMovableLimitTypeTask task = (SetMovableLimitTypeTask) guiTask;

				grid.setMovableLimitType(task.getPosition(), task.getMovableType(), task.isRelative());
				break;
			}

			default:
				break;

		}
	}

	private void destroyBuilding(SimpleBuildingGuiTask task) {
		this.forBuilding(task, Building::kill);
	}

	private void setAcceptedStockMaterial(SetAcceptedStockMaterialGuiTask guiTask) {
		ShortPoint2D taskPosition = guiTask.getPosition();

		if (guiTask.isLocal()) {
			IBuilding building = grid.getBuildingAt(taskPosition.x, taskPosition.y);
			if (building instanceof StockBuilding) {
				StockBuilding stock = (StockBuilding) building;
				stock.setAcceptedMaterial(guiTask.getMaterialType(), guiTask.isAccepted());
			}

		} else {
			grid.setAcceptedStockMaterial(taskPosition, guiTask.getMaterialType(), guiTask.isAccepted());
		}
	}

	private void changeTowerSoldiers(ChangeTowerSoldiersGuiTask soldierTask) {
		ShortPoint2D buildingPosition = soldierTask.getBuildingPos();
		OccupyingBuilding occupyingBuilding = (OccupyingBuilding) grid.getBuildingAt(buildingPosition.x, buildingPosition.y);

		if (occupyingBuilding == null) {
			return;
		}

		switch (soldierTask.getTaskType()) {
			case FULL:
				occupyingBuilding.requestFullSoldiers();
				break;
			case MORE:
				occupyingBuilding.requestSoldier(soldierTask.getSoldierType());
				break;
			case ONE:
				occupyingBuilding.releaseSoldiers();
				break;
			case LESS:
				occupyingBuilding.releaseSoldier(soldierTask.getSoldierType());
				break;
		}
	}

	private void save() {
		try {
			grid.save(playerId, guiInterface.getUIState());
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void setBuildingPriority(SetBuildingPriorityGuiTask task) {
		this.<Building>forBuilding(task, building -> building.setPriority(task.getNewPriority()));
	}

	private void convertMovables(ConvertGuiTask guiTask) {
		for (Integer currID : guiTask.getSelection()) {
			ILogicMovable movable = MovableManager.getMovableByID(currID);
			switch(guiTask.getTargetType()) {
				case GEOLOGIST:
				case PIONEER:
				case THIEF:
					if(movable instanceof IBearerMovable) {
						((IBearerMovable)movable).convertTo(guiTask.getTargetType());
					}
					break;
				case BEARER:
					if(movable instanceof IPioneerMovable) {
						((IPioneerMovable)movable).convertToBearer();
					}
					break;
				default:
					System.err.println("Illegal conversion from " + movable.getMovableType() + " to " + guiTask.getTargetType());
			}
		}
		guiInterface.renewSelection();
	}

	private void stopOrStartWorking(List<Integer> selectedMovables, boolean stop) {
		for (Integer currID : selectedMovables) {
			ILogicMovable movable = MovableManager.getMovableByID(currID);
			if (movable != null) {
				movable.stopOrStartWorking(stop);
			}
		}
	}

	private void killSelectedMovables(List<Integer> selectedMovables) {
		for (Integer currID : selectedMovables) {
			ILogicMovable curr = MovableManager.getMovableByID(currID);
			if (curr != null) {
				curr.kill();
			}
		}
	}

	/**
	 * Move the selected {@link Movable} to the given position.
	 *
	 * @param targetPosition
	 *            position to move to
	 * @param movableIds
	 *            A list of the id's of the movables.
	 * @param moveToType 
	 *            How to move there.
	 */
	private void moveSelectedTo(ShortPoint2D targetPosition, List<Integer> movableIds, EMoveToType moveToType) {
		List<ILogicMovable> movables = movableIds.stream().map(MovableManager::getMovableByID).filter(Objects::nonNull).collect(Collectors.toList());

		if (movables.isEmpty()) {
			return;
		}

		final FerryEntrance ferryEntrance;
		if (!movables.get(0).isShip() && grid.isBlocked(targetPosition.x, targetPosition.y)) {
			ferryEntrance = grid.ferryAtPosition(targetPosition, this.playerId);
		} else {
			ferryEntrance = null;
		}

		if (ferryEntrance != null) { // enter a ferry
			movables.stream().filter(movable -> movable instanceof IAttackableHumanMovable)
					.forEach(movable -> ((IAttackableHumanMovable)movable).moveToFerry(ferryEntrance.ferry, ferryEntrance.entrance));
		} else {
			sendManyMovables(targetPosition, movables, moveToType);
		}
	}

	private void castSpell(CastSpellGuiTask castSpellGuiTask) {
		ILogicMovable priest = MovableManager.getMovableByID(castSpellGuiTask.getSelection().get(0));

		if(!(priest instanceof IMageMovable)) return;

		((IMageMovable)priest).moveToCast(castSpellGuiTask.getAt(), castSpellGuiTask.getSpell());
	}

	private void sendManyMovables(ShortPoint2D targetPosition, List<ILogicMovable> movables, EMoveToType moveToType) {
		for (int radius = 0, ringsWithoutSuccessCtr = 0; ringsWithoutSuccessCtr <= Math.max(5, 15 - radius + ringsWithoutSuccessCtr) && !movables.isEmpty(); radius++) {
			MutableInt numberOfSendMovables = new MutableInt(0);

			HexGridArea
				.streamBorder(targetPosition.x, targetPosition.y, radius)
				.filterBounds(grid.getWidth(), grid.getHeight())
				.getEvery(2)
				.forEach((x, y) -> {
					Optional<ILogicMovable> movableOptional = removeMovableThatCanMoveTo(movables, x, y);

					movableOptional.ifPresent(movable -> {
						movable.moveTo(new ShortPoint2D(x, y), moveToType);
						numberOfSendMovables.value++;
					});
				});

			if (numberOfSendMovables.value > 0) {
				ringsWithoutSuccessCtr = 0;
			} else {
				ringsWithoutSuccessCtr++;
			}
		}
	}

	private Optional<ILogicMovable> removeMovableThatCanMoveTo(List<ILogicMovable> movables, int x, int y) {
		for (Iterator<ILogicMovable> iterator = movables.iterator(); iterator.hasNext(); ) {
			ILogicMovable movable = iterator.next();
			if (canMoveTo(movable, x, y)) {
				iterator.remove();
				return Optional.of(movable);
			}
		}
		return Optional.empty();
	}

	private boolean canMoveTo(ILogicMovable movable, int x, int y) {
		return (movable.isShip() && grid.isNavigable(x, y))
			|| (!grid.isBlocked(x, y) && grid.getBlockedPartition(movable.getPosition().x, movable.getPosition().y) == grid.getBlockedPartition(x, y));
	}

	private void setWorkArea(WorkAreaGuiTask task) {
		this.<Building>forBuilding(task, building -> building.setWorkAreaCenter(task.getPosition()));
	}

	public void setDock(SetDockGuiTask task) {
		this.<IDockBuilding>forBuilding(task, building -> building.setDock(task.getRequestedDockPosition()));
	}

	private void orderShip(OrderShipGuiTask task) {
		this.<DockyardBuilding>forBuilding(task, building -> building.orderShipType(task.getShipType()));
	}

	private void unloadFerry(MovableGuiTask task) {
		forFerries(task, IFerryMovable::unloadFerry);
	}

	private void forFerries(MovableGuiTask task, Consumer<IFerryMovable> movableConsumer) {
		task.getSelection().stream()
			.map(MovableManager::getMovableByID)
			.filter(ILogicMovable::isAlive)
			.filter(movable -> movable.getMovableType() == EMovableType.FERRY)
			.map(movable -> (IFerryMovable)movable)
			.forEach(movableConsumer);
	}

	private <T> void forBuilding(SimpleBuildingGuiTask buildingTask, Consumer<T> buildingConsumer) {
		ShortPoint2D buildingPos = buildingTask.getBuildingPos();
		// noinspection unchecked
		T building = (T) grid.getBuildingAt(buildingPos.x, buildingPos.y);

		if (building != null) {
			buildingConsumer.accept(building);
		}
	}
}
