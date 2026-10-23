"""Validate the Gradle distribution before allowing it into CI artifacts / Releases."""
from pathlib import Path
from zipfile import ZipFile
from io import BytesIO
import hashlib
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
from version_policy import properties as read_properties
import argparse
parser = argparse.ArgumentParser()
parser.add_argument("--distribution-dir", type=Path)
args = parser.parse_args()
properties = read_properties(root)
name, version = properties['pluginName'], properties['pluginVersion']
package = (args.distribution_dir or root / 'build/distributions') / f'{name}-{version}.zip'
with ZipFile(package) as archive:
    files = [n for n in archive.namelist() if not n.endswith('/')]
    assert files and all(n.startswith(name + '/') for n in files), 'Invalid plugin ZIP root'
    descriptors = []
    for filename in files:
        if filename.startswith(name + '/lib/') and filename.endswith('.jar'):
            with ZipFile(BytesIO(archive.read(filename))) as jar:
                assert not any(n.startswith('local/mianshiya/qa/') for n in jar.namelist()), 'QA fixture in distribution'
                if 'META-INF/plugin.xml' in jar.namelist():
                    descriptors.append(ET.fromstring(jar.read('META-INF/plugin.xml')))
    assert len(descriptors) == 1, 'Expected exactly one plugin descriptor'
    descriptor = descriptors[0]
    assert descriptor.findtext('id') == 'io.github.ataraxyadong.devreader'
    assert descriptor.findtext('name') == name
    assert descriptor.findtext('version') == version
    assert descriptor.find('extensions/toolWindow').get('id') == 'dev-reader'
    assert descriptor.find('actions/action').get('id') == 'DevReader.ToggleReader'
    compatibility = descriptor.find('idea-version')
    assert compatibility.get('since-build') == properties['pluginSinceBuild']
    assert compatibility.get('until-build') == properties['pluginUntilBuild']
checksum = hashlib.sha256(package.read_bytes()).hexdigest()
(package.parent / 'SHA256SUMS.txt').write_text(f'{checksum}  {package.name}\n')
print(f'Validated {package.name}: {checksum}')
