/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.util.Assert;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.Wizard;

import org.eclipse.ui.model.BaseWorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.actions.AbstractOpenWizardAction;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.PixelConverter;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.newsourcepage.LinkFolderDialog;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ITreeListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.TreeListDialogField;

public class SourceContainerWorkbookPage extends BuildPathBasePage {
	
	private class OpenAddSourceFolderWizardAction extends AbstractOpenWizardAction implements IPropertyChangeListener {
		
		AddSourceFolderWizard fAddSourceFolderWizard;

		public OpenAddSourceFolderWizardAction() {
			addPropertyChangeListener(this);
		}

		/**
		 * {@inheritDoc}
		 */
		protected Wizard createWizard() throws CoreException {
			List elements= fFoldersList.getElements();
			CPListElement[] existing= (CPListElement[])elements.toArray(new CPListElement[elements.size()]);
			CPListElement newElement= new CPListElement(fCurrJProject, IClasspathEntry.CPE_SOURCE);
			fAddSourceFolderWizard= new AddSourceFolderWizard(fCurrJProject, existing, newElement);
			fAddSourceFolderWizard.setDoFlushChange(false);
			return fAddSourceFolderWizard;
		}
		
		/**
		 * {@inheritDoc}
		 */
		public void propertyChange(PropertyChangeEvent event) {
			if (event.getProperty().equals(IAction.RESULT) && event.getNewValue().equals(Boolean.TRUE)) {
				finishWizard();
			}
		}
		
		protected void finishWizard() {
			CPListElement[] createdElements= fAddSourceFolderWizard.getCreatedElements();
			List elementsToAdd= Arrays.asList(createdElements);
			fFoldersList.addElements(elementsToAdd);
			for (int i= 0; i < createdElements.length; i++) {
				fFoldersList.expandElement(createdElements[i], 3);	
			}
			fFoldersList.postSetSelection(new StructuredSelection(elementsToAdd));
			
			fFoldersList.removeElements(Arrays.asList(fAddSourceFolderWizard.getRemovedElements()));
			CPListElement[] modifiedElements= fAddSourceFolderWizard.getModifiedElements();
			for (int i= 0; i < modifiedElements.length; i++) {
				fFoldersList.refresh(modifiedElements[i]);	
				fFoldersList.expandElement(modifiedElements[i], 3);
			}
			fOutputLocationField.setText(fAddSourceFolderWizard.getOutputLocation().makeRelative().toOSString());
		}
	}

	private ListDialogField fClassPathList;
	private IJavaProject fCurrJProject;
	
	private Control fSWTControl;
	
	private IWorkspaceRoot fWorkspaceRoot;
	
	private TreeListDialogField fFoldersList;
	
	private StringDialogField fOutputLocationField;
	
	private SelectionButtonDialogField fUseFolderOutputs;
	
	private final int IDX_ADD= 0;
	private final int IDX_ADD_LINK= 1;
	private final int IDX_EDIT= 3;
	private final int IDX_REMOVE= 4;	

	public SourceContainerWorkbookPage(ListDialogField classPathList, StringDialogField outputLocationField) {
		fWorkspaceRoot= ResourcesPlugin.getWorkspace().getRoot();
		fClassPathList= classPathList;
	
		fOutputLocationField= outputLocationField;
		
		fSWTControl= null;
				
		SourceContainerAdapter adapter= new SourceContainerAdapter();
					
		String[] buttonLabels;

		buttonLabels= new String[] { 
			NewWizardMessages.SourceContainerWorkbookPage_folders_add_button, 
			NewWizardMessages.SourceContainerWorkbookPage_folders_link_source_button,
			/* 1 */ null,
			NewWizardMessages.SourceContainerWorkbookPage_folders_edit_button, 
			NewWizardMessages.SourceContainerWorkbookPage_folders_remove_button
		};
		
		fFoldersList= new TreeListDialogField(adapter, buttonLabels, new CPListLabelProvider());
		fFoldersList.setDialogFieldListener(adapter);
		fFoldersList.setLabelText(NewWizardMessages.SourceContainerWorkbookPage_folders_label); 
		
		fFoldersList.setViewerSorter(new CPListElementSorter());
		fFoldersList.enableButton(IDX_EDIT, false);
		
		fUseFolderOutputs= new SelectionButtonDialogField(SWT.CHECK);
		fUseFolderOutputs.setSelection(false);
		fUseFolderOutputs.setLabelText(NewWizardMessages.SourceContainerWorkbookPage_folders_check); 
		fUseFolderOutputs.setDialogFieldListener(adapter);
	}
	
	public void init(IJavaProject jproject) {
		fCurrJProject= jproject;	
		updateFoldersList();
	}
	
	private void updateFoldersList() {	
		ArrayList folders= new ArrayList();
	
		boolean useFolderOutputs= false;
		List cpelements= fClassPathList.getElements();
		for (int i= 0; i < cpelements.size(); i++) {
			CPListElement cpe= (CPListElement)cpelements.get(i);
			if (cpe.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				folders.add(cpe);
				boolean hasOutputFolder= (cpe.getAttribute(CPListElement.OUTPUT) != null);
				if (hasOutputFolder) {
					useFolderOutputs= true;
				}

			}
		}
		fFoldersList.setElements(folders);
		fUseFolderOutputs.setSelection(useFolderOutputs);
		
		for (int i= 0; i < folders.size(); i++) {
			CPListElement cpe= (CPListElement) folders.get(i);
			IPath[] ePatterns= (IPath[]) cpe.getAttribute(CPListElement.EXCLUSION);
			IPath[] iPatterns= (IPath[])cpe.getAttribute(CPListElement.INCLUSION);
			boolean hasOutputFolder= (cpe.getAttribute(CPListElement.OUTPUT) != null);
			if (ePatterns.length > 0 || iPatterns.length > 0 || hasOutputFolder) {
				fFoldersList.expandElement(cpe, 3);
			}				
		}
	}			
	
	public Control getControl(Composite parent) {
		PixelConverter converter= new PixelConverter(parent);
		Composite composite= new Composite(parent, SWT.NONE);
		
		LayoutUtil.doDefaultLayout(composite, new DialogField[] { fFoldersList, fUseFolderOutputs }, true, SWT.DEFAULT, SWT.DEFAULT);
		LayoutUtil.setHorizontalGrabbing(fFoldersList.getTreeControl(null));
		
		int buttonBarWidth= converter.convertWidthInCharsToPixels(24);
		fFoldersList.setButtonsMinWidth(buttonBarWidth);
			
		fSWTControl= composite;
		
		// expand
		List elements= fFoldersList.getElements();
		for (int i= 0; i < elements.size(); i++) {
			CPListElement elem= (CPListElement) elements.get(i);
			IPath[] exclusionPatterns= (IPath[]) elem.getAttribute(CPListElement.EXCLUSION);
			IPath[] inclusionPatterns= (IPath[]) elem.getAttribute(CPListElement.INCLUSION);
			IPath output= (IPath) elem.getAttribute(CPListElement.OUTPUT);
			if (exclusionPatterns.length > 0 || inclusionPatterns.length > 0 || output != null) {
				fFoldersList.expandElement(elem, 3);
			}
		}
		return composite;
	}
	
	private Shell getShell() {
		if (fSWTControl != null) {
			return fSWTControl.getShell();
		}
		return JavaPlugin.getActiveWorkbenchShell();
	}
	
	
	private class SourceContainerAdapter implements ITreeListAdapter, IDialogFieldListener {
	
		private final Object[] EMPTY_ARR= new Object[0];
		
		// -------- IListAdapter --------
		public void customButtonPressed(TreeListDialogField field, int index) {
			sourcePageCustomButtonPressed(field, index);
		}
		
		public void selectionChanged(TreeListDialogField field) {
			sourcePageSelectionChanged(field);
		}
		
		public void doubleClicked(TreeListDialogField field) {
			sourcePageDoubleClicked(field);
		}
		
		public void keyPressed(TreeListDialogField field, KeyEvent event) {
			sourcePageKeyPressed(field, event);
		}	

		public Object[] getChildren(TreeListDialogField field, Object element) {
			if (element instanceof CPListElement) {
				return ((CPListElement) element).getChildren(!fUseFolderOutputs.isSelected());
			}
			return EMPTY_ARR;
		}

		public Object getParent(TreeListDialogField field, Object element) {
			if (element instanceof CPListElementAttribute) {
				return ((CPListElementAttribute) element).getParent();
			}
			return null;
		}

		public boolean hasChildren(TreeListDialogField field, Object element) {
			return (element instanceof CPListElement);
		}		
		
		// ---------- IDialogFieldListener --------
		public void dialogFieldChanged(DialogField field) {
			sourcePageDialogFieldChanged(field);
		}

	}
	
	protected void sourcePageKeyPressed(TreeListDialogField field, KeyEvent event) {
		if (field == fFoldersList) {
			if (event.character == SWT.DEL && event.stateMask == 0) {
				List selection= field.getSelectedElements();
				if (canRemove(selection)) {
					removeEntry();
				}
			}
		}	
	}	
	
	protected void sourcePageDoubleClicked(TreeListDialogField field) {
		if (field == fFoldersList) {
			List selection= field.getSelectedElements();
			if (canEdit(selection)) {
				editEntry();
			}
		}
	}
		
	protected void sourcePageCustomButtonPressed(DialogField field, int index) {
		if (field == fFoldersList) {
			if (index == IDX_ADD) {
				OpenAddSourceFolderWizardAction action= new OpenAddSourceFolderWizardAction();
				action.run();
			} else if (index == IDX_ADD_LINK) {
				List elementsToAdd= new ArrayList(10);
				CPListElement entry= openNewLinkedSourceContainerDialog(null);
				if (entry != null) {
					elementsToAdd.add(entry);
				}
				if (!elementsToAdd.isEmpty()) {
					if (fFoldersList.getSize() == 1) {
						CPListElement existing= (CPListElement) fFoldersList.getElement(0);
						if (existing.getResource() instanceof IProject) {
							askForChangingBuildPathDialog(existing);
						}
					}
					HashSet modifiedElements= new HashSet();
					askForAddingExclusionPatternsDialog(elementsToAdd, modifiedElements);
					
					fFoldersList.addElements(elementsToAdd);
					fFoldersList.postSetSelection(new StructuredSelection(elementsToAdd));
					
					if (!modifiedElements.isEmpty()) {
						for (Iterator iter= modifiedElements.iterator(); iter.hasNext();) {
							Object elem= iter.next();
							fFoldersList.refresh(elem);
							fFoldersList.expandElement(elem, 3);
						}
					}

				}				
			} else if (index == IDX_EDIT) {
				editEntry();
			} else if (index == IDX_REMOVE) {
				removeEntry();
			}
		}
	}

	private void editEntry() {
		List selElements= fFoldersList.getSelectedElements();
		if (selElements.size() != 1) {
			return;
		}
		Object elem= selElements.get(0);
		if (fFoldersList.getIndexOfElement(elem) != -1) {
			editElementEntry((CPListElement) elem);
		} else if (elem instanceof CPListElementAttribute) {
			editAttributeEntry((CPListElementAttribute) elem);
		}
	}

	private void editElementEntry(CPListElement elem) {
		CPListElement res= null;
		
		if (elem.getLinkTarget() != null) {
			removeEntry();
			CPListElement entry= openNewLinkedSourceContainerDialog(elem);
			
			if (entry == null)
				entry= elem;
			
			fFoldersList.addElement(entry);
			fFoldersList.postSetSelection(new StructuredSelection(entry));
		} else {
			res= editSourceContainerDialog(elem);
			
			if (res != null) {
				fFoldersList.replaceElement(elem, res);
			}
		}
	}

	private void editAttributeEntry(CPListElementAttribute elem) {
		String key= elem.getKey();
		if (key.equals(CPListElement.OUTPUT)) {
			CPListElement selElement=  elem.getParent();
			OutputLocationDialog dialog= new OutputLocationDialog(getShell(), selElement, fClassPathList.getElements());
			if (dialog.open() == Window.OK) {
				selElement.setAttribute(CPListElement.OUTPUT, dialog.getOutputLocation());
				fFoldersList.refresh();
				fClassPathList.dialogFieldChanged(); // validate
			}
		} else if (key.equals(CPListElement.EXCLUSION)) {
			showExclusionInclusionDialog(elem.getParent(), true);		
		} else if (key.equals(CPListElement.INCLUSION)) {
			showExclusionInclusionDialog(elem.getParent(), false);
		}
	}

	private void showExclusionInclusionDialog(CPListElement selElement, boolean focusOnExclusion) {
		ExclusionInclusionDialog dialog= new ExclusionInclusionDialog(getShell(), selElement, focusOnExclusion);
		if (dialog.open() == Window.OK) {
			selElement.setAttribute(CPListElement.INCLUSION, dialog.getInclusionPattern());
			selElement.setAttribute(CPListElement.EXCLUSION, dialog.getExclusionPattern());
			fFoldersList.refresh();
			fClassPathList.dialogFieldChanged(); // validate
		}
	}

	protected void sourcePageSelectionChanged(DialogField field) {
		List selected= fFoldersList.getSelectedElements();
		fFoldersList.enableButton(IDX_EDIT, canEdit(selected));
		fFoldersList.enableButton(IDX_REMOVE, canRemove(selected));
		boolean noAttributes= containsOnlyTopLevelEntries(selected);
		fFoldersList.enableButton(IDX_ADD, noAttributes);
	}
	
	private void removeEntry() {
		List selElements= fFoldersList.getSelectedElements();
		for (int i= selElements.size() - 1; i >= 0 ; i--) {
			Object elem= selElements.get(i);
			if (elem instanceof CPListElementAttribute) {
				CPListElementAttribute attrib= (CPListElementAttribute) elem;
				String key= attrib.getKey();
				Object value= null;
				if (key.equals(CPListElement.EXCLUSION) || key.equals(CPListElement.INCLUSION)) {
					value= new Path[0];
				}
				attrib.getParent().setAttribute(key, value);
				selElements.remove(i);
			}
		}
		if (selElements.isEmpty()) {
			fFoldersList.refresh();
			fClassPathList.dialogFieldChanged(); // validate
		} else {
			fFoldersList.removeElements(selElements);
		}
	}
	
	private boolean canRemove(List selElements) {
		if (selElements.size() == 0) {
			return false;
		}
		for (int i= 0; i < selElements.size(); i++) {
			Object elem= selElements.get(i);
			if (elem instanceof CPListElementAttribute) {
				CPListElementAttribute attrib= (CPListElementAttribute) elem;
				String key= attrib.getKey();
				if (CPListElement.INCLUSION.equals(key)) {
					if (((IPath[]) attrib.getValue()).length == 0) {
						return false;
					}
				} else if (CPListElement.EXCLUSION.equals(key)) {
					if (((IPath[]) attrib.getValue()).length == 0) {
						return false;
					}
				} else if (attrib.getValue() == null) {
					return false;
				}
			} else if (elem instanceof CPListElement) {
				CPListElement curr= (CPListElement) elem;
				if (curr.getParentContainer() != null) {
					return false;
				}
			}
		}
		return true;
	}		
	
	private boolean canEdit(List selElements) {
		if (selElements.size() != 1) {
			return false;
		}
		Object elem= selElements.get(0);
		if (elem instanceof CPListElement) {
			return true;
		}
		if (elem instanceof CPListElementAttribute) {
			return true;
		}
		return false;
	}	
	
	private void sourcePageDialogFieldChanged(DialogField field) {
		if (fCurrJProject == null) {
			// not initialized
			return;
		}
		
		if (field == fUseFolderOutputs) {
			if (!fUseFolderOutputs.isSelected()) {
				int nFolders= fFoldersList.getSize();
				for (int i= 0; i < nFolders; i++) {
					CPListElement cpe= (CPListElement) fFoldersList.getElement(i);
					cpe.setAttribute(CPListElement.OUTPUT, null);
				}
			}
			fFoldersList.refresh();
		} else if (field == fFoldersList) {
			updateClasspathList();
		}
	}	
	
		
	private void updateClasspathList() {
		List srcelements= fFoldersList.getElements();
		
		List cpelements= fClassPathList.getElements();
		int nEntries= cpelements.size();
		// backwards, as entries will be deleted
		int lastRemovePos= nEntries;
		int afterLastSourcePos= 0;
		for (int i= nEntries - 1; i >= 0; i--) {
			CPListElement cpe= (CPListElement)cpelements.get(i);
			int kind= cpe.getEntryKind();
			if (isEntryKind(kind)) {
				if (!srcelements.remove(cpe)) {
					cpelements.remove(i);
					lastRemovePos= i;
				} else if (lastRemovePos == nEntries) {
					afterLastSourcePos= i + 1;
				}
			}
		}

		if (!srcelements.isEmpty()) {
			int insertPos= Math.min(afterLastSourcePos, lastRemovePos);
			cpelements.addAll(insertPos, srcelements);
		}
		
		if (lastRemovePos != nEntries || !srcelements.isEmpty()) {
			fClassPathList.setElements(cpelements);
		}
	}
	
    private CPListElement openNewLinkedSourceContainerDialog(CPListElement element) {
    	
    	LinkFolderDialog dialog= new LinkFolderDialog(getShell(), fCurrJProject.getProject(), false);
    	if (element != null) {
	    	dialog.setName(element.getResource().getName());
	    	dialog.setLinkTarget(element.getLinkTarget().toOSString());
    	}
    	
        if (dialog.open() == Window.OK) {
            IResource createdLink= dialog.getCreatedFolder();
            IPath linkTarget= dialog.getLinkTarget();
            CPListElement result= new CPListElement(fCurrJProject, IClasspathEntry.CPE_SOURCE, createdLink.getFullPath(), createdLink);
            result.setLinkTarget(linkTarget);
			return result;
        }
		return null;
	}
    
    private CPListElement editSourceContainerDialog(CPListElement existing) {
    		
		Class[] acceptedClasses= new Class[] { IProject.class, IFolder.class };
		List existingContainers= getExistingContainers(null);
		IProject[] allProjects= fWorkspaceRoot.getProjects();
		ArrayList rejectedElements= new ArrayList(allProjects.length);
		IProject currProject= fCurrJProject.getProject();
		for (int i= 0; i < allProjects.length; i++) {
			if (!allProjects[i].equals(currProject)) {
				rejectedElements.add(allProjects[i]);
			}
		}
		rejectedElements.addAll(existingContainers);
		ViewerFilter filter= new TypedViewerFilter(acceptedClasses, rejectedElements.toArray());
		
		ILabelProvider lp= new WorkbenchLabelProvider();
		ITreeContentProvider cp= new BaseWorkbenchContentProvider();

		String title= NewWizardMessages.SourceContainerWorkbookPage_ExistingSourceFolderDialog_edit_title; 
		String message= NewWizardMessages.SourceContainerWorkbookPage_ExistingSourceFolderDialog_edit_description; 
		
		FolderSelectionDialog dialog= new FolderSelectionDialog(getShell(), lp, cp);

		dialog.setTitle(title);
		dialog.setMessage(message);
		dialog.addFilter(filter);
		dialog.setInput(fCurrJProject.getProject().getParent());
		if (dialog.open() == Window.OK) {
			Object[] elements= dialog.getResult();	
			if (elements.length == 1)
				return newCPSourceElement((IResource)elements[0]);
		}
		return null;
    }
	
	/**
	 * Asks to change the output folder to 'proj/bin' when no source folders were existing
	 */ 
	private void askForChangingBuildPathDialog(CPListElement existing) {
		IPath outputFolder= new Path(fOutputLocationField.getText());
		
		IPath newOutputFolder= null;
		String message;
		if (outputFolder.segmentCount() == 1) {
			String outputFolderName= PreferenceConstants.getPreferenceStore().getString(PreferenceConstants.SRCBIN_BINNAME);
			newOutputFolder= outputFolder.append(outputFolderName);
			message= Messages.format(NewWizardMessages.SourceContainerWorkbookPage_ChangeOutputLocationDialog_project_and_output_message, newOutputFolder); 
		} else {
			message= NewWizardMessages.SourceContainerWorkbookPage_ChangeOutputLocationDialog_project_message; 
		}
		String title= NewWizardMessages.SourceContainerWorkbookPage_ChangeOutputLocationDialog_title; 
		if (MessageDialog.openQuestion(getShell(), title, message)) {
			fFoldersList.removeElement(existing);
			if (newOutputFolder != null) {
				fOutputLocationField.setText(newOutputFolder.toString());
			}
		}			
	}
	
	private void askForAddingExclusionPatternsDialog(List newEntries, Set modifiedEntries) {
		fixNestingConflicts(newEntries, fFoldersList.getElements(), modifiedEntries);
		if (!modifiedEntries.isEmpty()) {
			String title= NewWizardMessages.SourceContainerWorkbookPage_exclusion_added_title; 
			String message= NewWizardMessages.SourceContainerWorkbookPage_exclusion_added_message; 
			MessageDialog.openInformation(getShell(), title, message);
		}
	}
	
	private List getExistingContainers(CPListElement existing) {
		List res= new ArrayList();
		List cplist= fFoldersList.getElements();
		for (int i= 0; i < cplist.size(); i++) {
			CPListElement elem= (CPListElement)cplist.get(i);
			if (elem != existing) {
				IResource resource= elem.getResource();
				if (resource instanceof IContainer) { // defensive code
					res.add(resource);	
				}
			}
		}
		return res;
	}
	
	private CPListElement newCPSourceElement(IResource res) {
		Assert.isNotNull(res);
		return new CPListElement(fCurrJProject, IClasspathEntry.CPE_SOURCE, res.getFullPath(), res);
	}
	
	/*
	 * @see BuildPathBasePage#getSelection
	 */
	public List getSelection() {
		return fFoldersList.getSelectedElements();
	}

	/*
	 * @see BuildPathBasePage#setSelection
	 */	
	public void setSelection(List selElements, boolean expand) {
		fFoldersList.selectElements(new StructuredSelection(selElements));
		if (expand) {
			for (int i= 0; i < selElements.size(); i++) {
				fFoldersList.expandElement(selElements.get(i), 1);
			}
		}
	}	
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathBasePage#isEntryKind(int)
	 */
	public boolean isEntryKind(int kind) {
		return kind == IClasspathEntry.CPE_SOURCE;
	}	

}
