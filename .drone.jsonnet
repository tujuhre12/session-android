local docker_base = 'registry.oxen.rocks/lokinet-ci-';
local default_deps = [];

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

local debian_pipeline(name,
                      image,
                      arch='amd64',
                      deps=default_deps,
                      oxen_repo=false,
                      kitware_repo=''/* ubuntu codename, if wanted */,
                      allow_fail=false,
                      build=['echo "Error: drone build argument not set"', 'exit 1'])
      = {
  kind: 'pipeline',
  type: 'docker',
  name: name,
  platform: { arch: arch },
  steps: [
    submodules,
    {
      name: 'build',
      image: image,
      pull: 'always',
      [if allow_fail then 'failure']: 'ignore',
      environment: { SSH_KEY: { from_secret: 'SSH_KEY' }, WINEDEBUG: '-all' },
      commands: [] + build,
    },
  ],
};

local debian_build(name,
                   image,
                   arch='amd64',
                   deps=default_deps,
                   build_type='Release',
                   lto=false,
                   werror=true,
                   cmake_extra='',
                   jobs=6,
                   tests=true,
                   oxen_repo=false,
                   kitware_repo=''/* ubuntu codename, if wanted */,
                   allow_fail=false)
      = debian_pipeline(
  name,
  image,
  arch=arch,
  deps=deps,
  oxen_repo=oxen_repo,
  kitware_repo=kitware_repo,
  allow_fail=allow_fail,
  build=[
    './gradlew assemblePlayDebug',
  ]
);


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
      clone_submodules,
      debian_pipeline(
        'Build',
        docker_base + 'android',
        build=[]
      ),
      {
        name: 'Upload artifacts',
        image: docker_base + 'android',
        environment: { SSH_KEY: { from_secret: 'SSH_KEY' } },
        commands: [
          './Scripts/drone-static-upload.sh'
        ]
      },
    ],
  }
]