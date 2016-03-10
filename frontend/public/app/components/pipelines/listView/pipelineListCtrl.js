define([], function () {
    function controler($scope, $location, $http, $timeout, $mdDialog,
            refreshService, repositoryService, statusService) {

        var listDecorator = function (item) {
            item.waitForDelete = false;
        };

        var listOnDelete = function (item) {
            item.waitForDelete = true;
        };

        $scope.repository = repositoryService.createRepository({
            'uri': '/resources/pipelines',
            'updateOperation': listDecorator,
            'deleteOperation': listOnDelete
        });

        $scope.onPipeline = function (pipeline) {
            $location.path('/pipelines/edit/canvas').search({'pipeline': pipeline.uri});
        };

        $scope.onExecute = function (pipeline) {
            $http.post('/api/v1/execute?uri=' + pipeline.uri).then(function (response) {
                $location.path('/executions').search({});
            }, function (response) {
                statusService.postFailed({
                    'title': "Can't start the execution.",
                    'response': response
                });
            });
        };

        $scope.onCreate = function () {
            // Create pipeline.
            var id = $scope.id = 'created-' + (new Date()).getTime();
            $http.post('/resources/pipelines/' + id).then(function (response) {
                $location.path('/pipelines/edit/canvas').search({'pipeline': response.data.uri});
            }, function (response) {
                statusService.postFailed({
                    'title': "Can't create the pipeline.",
                    'response': response
                });
            });
            // TODO We may try a few time here, although the chance that two users click in the exactly same unix time
            // is rather small.
        };

        $scope.onUpload = function () {
            $location.path('/pipelines/upload').search({});
        };

        $scope.openMenu = function ($mdOpenMenu, ev) {
            $mdOpenMenu(ev);
        };

        $scope.onCopy = function (pipeline) {
            var id = 'created-' + (new Date()).getTime();
            var url = '/resources/pipelines/' + id + '?pipeline=' + pipeline.uri;
            $http.post(url).then(function (response) {
                statusService.success({
                    'title': 'Pipeline has been successfully copied.'
                });
                // Force update.
                repositoryService.get($scope.repository);
            }, function (response) {
                statusService.postFailed({
                    'title': "Can't copy pipeline.",
                    'response': response
                });
            });
        };

        $scope.onDelete = function (pipeline, event) {
            var confirm = $mdDialog.confirm()
                    .title('Would you like to delete pipeline "' + pipeline.label + '"?')
                    .ariaLabel('Delete pipeline.')
                    .targetEvent(event)
                    .ok('Delete pipeline')
                    .cancel('Cancel');
            $mdDialog.show(confirm).then(function () {
                // Delete pipeline.
                $http({method: 'DELETE', url: pipeline.uri}).then(function (response) {
                    // Force update.
                    repositoryService.get($scope.repository);
                }, function (response) {
                    statusService.deleteFailed({
                        'title': "Can't delete the pipeline.",
                        'response': response
                    });
                });
            });
        };

        var initialize = function () {
            repositoryService.get($scope.repository, function () {
            }, function (response) {
                statusService.getFailed({
                    'title': "Can't load data.",
                    'response': response
                });
            });
            refreshService.set(function () {
                // TODO Enable update !
                //repositoryService.update($scope.repository);
            });
        };
        $timeout(initialize, 0);
    }
    //
    controler.$inject = ['$scope', '$location', '$http', '$timeout',
        '$mdDialog', 'service.refresh', 'services.repository',
        'services.status'];
    //
    function init(app) {
        app.controller('components.pipelines.list', controler);
    }
    return init;
});