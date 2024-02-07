local docker_base = 'registry.oxen.rocks/lokinet-ci-';

// Log a bunch of version information to make it easier for debugging
local version_info = {
  name: 'Version Information',
  commands: [
    '/usr/lib/android-ndk --version',
    '/usr/lib/android-sdk --version'
  ]
};

// Intentionally doing a depth of 2 as libSession-util has it's own submodules (and libLokinet likely will as well)
local clone_submodules = {
  name: 'Clone Submodules',
  commands: ['git fetch --tags', 'git submodule update --init --recursive --depth=2 --jobs=4']
};

// cmake options for static deps mirror
local ci_dep_mirror(want_mirror) = (if want_mirror then ' -DLOCAL_MIRROR=https://oxen.rocks/deps ' else '');

[
  // Unit tests (PRs only)
  {
    kind: 'pipeline',
    type: 'docker',
    name: 'Unit Tests',
    platform: { arch: 'amd64' },
    trigger: { event: { exclude: [ 'push' ] } },
    steps: [
      version_info,
      clone_submodules,
      {
        name: 'Run Unit Tests',
        pull: always
        image: docker_base + 'android',
        commands: [
          './gradlew testPlayDebugUnitTestCoverageReport'
        ],
      }
    ],
  },
  // Validate build artifact was created by the direct branch push (PRs only)
  {
    kind: 'pipeline',
    type: 'docker',
    name: 'Check Build Artifact Existence',
    platform: { arch: 'amd64' },
    trigger: { event: { exclude: [ 'push' ] } },
    steps: [
      {
        name: 'Poll for build artifact existence',
        pull: always
        image: docker_base + 'android',
        commands: [
          './Scripts/drone-upload-exists.sh'
        ]
      }
    ]
  },
  // Debug APK build (non-PRs only)
  {
    kind: 'pipeline',
    type: 'docker',
    name: 'Debug APK Build',
    platform: { arch: 'amd64' },
    trigger: { event: { exclude: [ 'pull_request' ] } },
    steps: [
      version_info,
      clone_submodules,
      {
        name: 'Build',
        pull: always
        image: docker_base + 'android',
        commands: [
          './gradlew assemblePlayDebug'
        ],
      },
      {
        name: 'Upload artifacts',
        pull: always
        image: docker_base + 'android',
        environment: { SSH_KEY: { from_secret: 'SSH_KEY' } },
        commands: [
          './Scripts/drone-static-upload.sh'
        ]
      },
    ],
  }
]