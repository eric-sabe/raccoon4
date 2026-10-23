/*
 * Copyright 2015 Patrick Ahlbrecht
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.onyxbits.raccoon.setup;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import de.onyxbits.raccoon.gplay.PlayProfile;
import de.onyxbits.raccoon.gplay.SyntheticDevice;
import de.onyxbits.raccoon.gui.HyperTextPane;

public class DeviceLogic extends WizardBuilder implements ActionListener {

	private JRadioButton existing;
	private JRadioButton mock;
	private JButton loadPropsButton;
	private JLabel profileSummary;
	private SyntheticDevice syntheticDevice;

	@Override
	protected JPanel assemble() {
		syntheticDevice = SyntheticDevice.createDefault();

		HyperTextPane about = new HyperTextPane(
				Messages.getString("DeviceLogic.about")).withWidth(500)
				.withTransparency();
		mock = new JRadioButton(Messages.getString("DeviceLogic.mock"));
		existing = new JRadioButton(Messages.getString("DeviceLogic.existing"));
		ButtonGroup bg = new ButtonGroup();
		bg.add(mock);
		bg.add(existing);
		mock.setSelected(true);
		mock.addActionListener(this);
		existing.addActionListener(this);

		loadPropsButton = new JButton(
				"Load DummyDroid / build.prop File (Optional)...");
		loadPropsButton.addActionListener(this);
		profileSummary = new JLabel(formatSummary(syntheticDevice, null));

		JPanel ret = new JPanel();
		ret.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = GridBagConstraints.REMAINDER;
		gbc.gridy = GridBagConstraints.RELATIVE;
		gbc.insets = new Insets(4, 4, 12, 4);
		JPanel choices = new JPanel();
		choices.setLayout(new BoxLayout(choices, BoxLayout.Y_AXIS));
		choices.add(mock);
		choices.add(profileSummary);
		choices.add(loadPropsButton);
		choices.add(existing);
		ret.add(about, gbc);
		ret.add(choices, gbc);

		return ret;
	}

	private static String formatSummary(SyntheticDevice dev, File source) {
		String prefix = (source != null) ? source.getName() : "Built-in DummyDroid";
		return "   [" + prefix + ": " + dev.getManufacturer() + " " + dev.getModel()
				+ " / Android " + dev.getReleaseVersion() + " (SDK "
				+ dev.getSdkVersion() + ")]";
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == loadPropsButton) {
			JFileChooser chooser = new JFileChooser(
					new File(System.getProperty("user.dir")));
			int rv = chooser.showOpenDialog(loadPropsButton);
			if (rv == JFileChooser.APPROVE_OPTION) {
				try {
					File selected = chooser.getSelectedFile();
					syntheticDevice = SyntheticDevice.fromFile(selected);
					profileSummary
							.setText(formatSummary(syntheticDevice, selected));
					mock.setSelected(true);
				}
				catch (Exception ex) {
					JOptionPane.showMessageDialog(loadPropsButton,
							"Could not parse DummyDroid / build.prop file: "
									+ ex.getMessage(),
							"Invalid Profile File", JOptionPane.ERROR_MESSAGE);
				}
			}
		}
		loadPropsButton.setEnabled(mock.isSelected());
	}

	@Override
	protected void onActivate() {
		previous.setEnabled(true);
		next.setEnabled(true);
		loadPropsButton.setEnabled(mock.isSelected());
	}

	@Override
	protected void onNext() {
		if (mock.isSelected()) {
			if (syntheticDevice == null) {
				syntheticDevice = SyntheticDevice.createDefault();
			}
			globals.put(syntheticDevice);
			PlayProfile pp = globals.get(PlayProfile.class);
			pp.setAgent(syntheticDevice.toFinskyUserAgent());
			pp.setGsfId(null);
			show(UploadLogic.class);
		}
		else {
			show(MimicLogic.class);
		}
	}

	@Override
	protected void onPrevious() {
		show(AccountLogic.class);
	}

}
