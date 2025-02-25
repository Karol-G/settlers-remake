/*
 * Copyright (c) 2017 - 2018
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package jsettlers.main.android.core.controls;

import go.graphics.GLDrawContext;
import go.graphics.UIPoint;
import go.graphics.event.mouse.GODrawEvent;
import java.util.Optional;
import jsettlers.common.action.Action;
import jsettlers.common.action.AskCastSpellAction;
import jsettlers.common.action.BuildAction;
import jsettlers.common.action.CastSpellAction;
import jsettlers.common.action.EActionType;
import jsettlers.common.action.EMoveToType;
import jsettlers.common.action.IAction;
import jsettlers.common.action.PointAction;
import jsettlers.common.action.SetDockAction;
import jsettlers.common.action.SetTradingWaypointAction;
import jsettlers.common.action.ShowConstructionMarksAction;
import jsettlers.common.map.shapes.MapRectangle;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.common.selectable.ISelectionSet;
import jsettlers.graphics.action.ActionFireable;
import jsettlers.graphics.action.AskSetTradingWaypointAction;
import jsettlers.graphics.map.MapDrawContext;
import jsettlers.graphics.map.controls.IControls;

/**
 * Created by tompr on 21/11/2016.
 */
public class AndroidControls implements IControls, ActionFireable, TaskControls {
	private final ControlsAdapter controlsAdapter;

	private ActionFireable actionFireable;

	private ISelectionSet selection;

	/**
	 * A task is something that requires multiple steps. E.g. show constructions markers, then choose build location. If an action is part of a task then store it so we know how to react when the next
	 * action comes in.
	 */
	private IAction taskAction;

	public AndroidControls(ControlsAdapter controlsAdapter) {
		this.controlsAdapter = controlsAdapter;
	}

	/**
	 * The action is being sent to the game, here we just store it if it's part of a task. Depending on what's active and what's coming in we may need to start a new task, update the current task or
	 * end the current task.
	 */
	@Override
	public void action(IAction action) {
		switch (action.getActionType()) {
		case SHOW_CONSTRUCTION_MARK:
			ShowConstructionMarksAction showConstructionMarksAction = (ShowConstructionMarksAction) action;
			if (showConstructionMarksAction.getBuildingType() != null) { // null means dismissing the construction markers, so is not awaiting further actions
				if (taskAction != null && taskAction.getActionType() == EActionType.SHOW_CONSTRUCTION_MARK) {
					updateTask(action);
				} else {
					startTask(action);
				}
			}
			break;
		case ASK_SET_WORK_AREA:
		case ASK_CAST_SPELL:
		case ASK_SET_DOCK:
		case ASK_SET_TRADING_WAYPOINT:
			startTask(action);
			break;
		case SET_WORK_AREA:
		case CAST_SPELL:
		case SET_DOCK:
		case SET_TRADING_WAYPOINT:
		case BUILD:
		case ABORT:
			endTask();
			break;
		}

		controlsAdapter.onAction(action);
	}

	/**
	 * Replace the action based on the current task/selection. E.g SELECT_POINT may be choosing a build location or moving soldiers depending on what's current
	 */
	@Override
	public IAction replaceAction(IAction action) {
		if (action.getActionType() == EActionType.SELECT_POINT) {
			PointAction pointAction = (PointAction) action;

			if (taskAction != null) {
				switch (taskAction.getActionType()) {
				case SHOW_CONSTRUCTION_MARK:
					ShowConstructionMarksAction showConstructionMarksAction = (ShowConstructionMarksAction) taskAction;
					return new BuildAction(showConstructionMarksAction.getBuildingType(), pointAction.getPosition());
				case ASK_SET_WORK_AREA:
					return new PointAction(EActionType.SET_WORK_AREA, pointAction.getPosition());
				case ASK_CAST_SPELL:
					return new CastSpellAction(((AskCastSpellAction)taskAction).getSpell(), pointAction.getPosition());
				case ASK_SET_DOCK:
					return new SetDockAction(pointAction.getPosition());
				case ASK_SET_TRADING_WAYPOINT:
					AskSetTradingWaypointAction askSetTradingWaypointAction = (AskSetTradingWaypointAction) taskAction;
					return new SetTradingWaypointAction(askSetTradingWaypointAction.getWaypoint(), pointAction.getPosition());
				}
			}

			// if (selection != null && selection.getSize() > 0 && (selection.getSelectionType() == ESelectionType.SOLDIERS || selection.getSelectionType() == ESelectionType.SPECIALISTS)) {
			// return new PointAction(EActionType.MOVE_TO, pointAction.getPosition());
			// }
		} else if (action.getActionType() == EActionType.MOVE_TO) {
			PointAction pointAction = (PointAction) action;

			if (selection == null || selection.getSize() == 0) {
				return null;
			}
		}

		return action;
	}

	@Override
	public void displaySelection(ISelectionSet selection) {
		this.selection = selection;
		endTask();
		controlsAdapter.onSelection(selection);
	}

	@Override
	public void drawAt(GLDrawContext gl) {
		controlsAdapter.onDraw();
	}

	@Override
	public void resizeTo(float newWidth, float newHeight) {
	}

	@Override
	public boolean containsPoint(UIPoint position) {
		return false;
	}

	@Override
	public String getDescriptionFor(UIPoint position) {
		return null;
	}

	@Override
	public void setMapViewport(MapRectangle screenArea, ShortPoint2D displayCenter) {
		controlsAdapter.onPositionChanged(screenArea, displayCenter);
	}

	@Override
	public Optional<Action> getActionForMoveTo(UIPoint position, EMoveToType moveToType) {
		return Optional.empty();
	}

	@Override
	public Optional<Action> getActionForSelect(UIPoint position) {
		return Optional.empty();
	}

	@Override
	public boolean handleDrawEvent(GODrawEvent event) {
		return false;
	}

	@Override
	public void setDrawContext(ActionFireable actionFireable, MapDrawContext context) {
		this.actionFireable = actionFireable;
	}

	@Override
	public String getMapTooltip(ShortPoint2D point) {
		return null;
	}

	@Override
	public void stop() {
	}

	/**
	 * ActionFireable implementation
	 */
	@Override
	public void fireAction(IAction action) {
		actionFireable.fireAction(action);
	}

	/**
	 * TaskControls implementation
	 *
	 * @return
	 */
	@Override
	public boolean isTaskActive() {
		return taskAction != null && taskAction.getActionType() != EActionType.MOVE_TO;
	}

	@Override
	public void endTask() {
		if (taskAction != null) {
			switch (taskAction.getActionType()) {
			case SHOW_CONSTRUCTION_MARK:
				fireAction(new ShowConstructionMarksAction(null));
				break;
			default:
				fireAction(new Action(EActionType.ABORT));
				break;
			}

			taskAction = null;
		}
	}

	private void startTask(IAction action) {
		endTask();
		taskAction = action;
	}

	private void updateTask(IAction action) {
		taskAction = action;
	}
}
